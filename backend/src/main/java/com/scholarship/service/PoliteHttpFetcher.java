package com.scholarship.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fetches public pages the way a well-behaved crawler does:
 * <ul>
 *   <li>obeys robots.txt (cached per host) and identifies itself honestly;</li>
 *   <li>spaces requests to the same host (default 1.5 s);</li>
 *   <li>retries timeouts, 429 and 5xx with exponential backoff (honouring Retry-After), never retries other 4xx or TLS errors;</li>
 *   <li>treats bot challenges, CAPTCHAs and access-denied pages as "blocked" and stops. It never tries to get around them.</li>
 * </ul>
 */
@Component
public class PoliteHttpFetcher implements PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PoliteHttpFetcher.class);
    static final String USER_AGENT = "Mozilla/5.0 (compatible; ScholarAI-SourceMonitor/1.0)";
    private static final String AGENT_TOKEN = "scholarai";
    private static final long ROBOTS_TTL_MS = 12L * 60 * 60 * 1000;
    private static final Pattern CHALLENGE = Pattern.compile(
            "just a moment|cf-chl|captcha|attention required|access denied|verify you are (a )?human|are you a robot", Pattern.CASE_INSENSITIVE);

    private record CachedRobots(RobotsRules rules, long fetchedAt) {
    }

    private final int timeoutMs;
    private final int maxAttempts;
    private final long minIntervalMs;
    private final long backoffBaseMs;
    private final boolean respectRobots;
    private final Map<String, Long> lastRequestAt = new HashMap<>();
    private final Map<String, CachedRobots> robotsCache = new HashMap<>();

    @Autowired
    public PoliteHttpFetcher(@Value("${app.ingestion.fetch-timeout-ms:15000}") int timeoutMs,
                             @Value("${app.ingestion.max-attempts:3}") int maxAttempts,
                             @Value("${app.ingestion.min-interval-ms:1500}") long minIntervalMs,
                             @Value("${app.ingestion.respect-robots:true}") boolean respectRobots) {
        this(timeoutMs, maxAttempts, minIntervalMs, 1000, respectRobots);
    }

    PoliteHttpFetcher(int timeoutMs, int maxAttempts, long minIntervalMs, long backoffBaseMs, boolean respectRobots) {
        this.timeoutMs = timeoutMs;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.minIntervalMs = minIntervalMs;
        this.backoffBaseMs = backoffBaseMs;
        this.respectRobots = respectRobots;
    }

    @Override
    public Page fetch(String url) {
        URI uri;
        try {
            uri = URI.create(url);
            if (uri.getHost() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                return Page.failure(null, "Unsupported URL");
            }
        } catch (Exception e) {
            return Page.failure(null, "Invalid URL");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String pathAndQuery = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());

        if (respectRobots && !robotsFor(uri).allows(pathAndQuery)) {
            return Page.failure(null, "ROBOTS_DISALLOWED: robots.txt does not allow fetching this path");
        }

        Page last = Page.failure(null, "not attempted");
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            throttle(host);
            try {
                Connection.Response res = Jsoup.connect(url).userAgent(USER_AGENT).timeout(timeoutMs)
                        .maxBodySize(3 * 1024 * 1024).followRedirects(true).ignoreHttpErrors(true).ignoreContentType(true).execute();
                int status = res.statusCode();
                String body = res.body() == null ? "" : res.body();
                if ((status == 403 || status == 429 || status == 503 || status == 401) && CHALLENGE.matcher(head(body)).find()) {
                    return Page.failure(status, "BLOCKED: the site answered with a bot challenge or access restriction; not bypassed");
                }
                if (status == 401) {
                    return Page.failure(status, "AUTH_REQUIRED: login required; not attempted");
                }
                if (status == 429 || status >= 500) {
                    last = Page.failure(status, "HTTP " + status);
                    sleep(retryDelayMs(res.header("Retry-After"), attempt));
                    continue;
                }
                if (status >= 400) {
                    return Page.failure(status, "HTTP " + status);
                }
                org.jsoup.nodes.Document doc = Jsoup.parse(body, url);
                String text = doc.text();
                if (text.length() < 3000 && CHALLENGE.matcher(text).find()) {
                    return Page.failure(status, "BLOCKED: the page is a CAPTCHA or access-check screen; not bypassed");
                }
                return new Page(true, status, text, null, body);
            } catch (SSLException e) {
                return Page.failure(null, "SSLException: " + String.valueOf(e.getMessage()));   // retrying cannot fix a certificate problem
            } catch (IOException e) {
                last = Page.failure(null, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
                sleep(retryDelayMs(null, attempt));
            } catch (Exception e) {
                return Page.failure(null, e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            }
        }
        return last;
    }

    private static String head(String body) {
        return body.length() > 4000 ? body.substring(0, 4000) : body;
    }

    private long retryDelayMs(String retryAfter, int attempt) {
        long backoff = backoffBaseMs * (1L << (attempt - 1));
        if (retryAfter != null) {
            try {
                backoff = Math.max(backoff, Math.min(10_000, Long.parseLong(retryAfter.trim()) * 1000));
            } catch (NumberFormatException ignored) {
                // an HTTP-date Retry-After is not worth parsing here; the backoff applies
            }
        }
        return Math.min(backoff, 15_000);
    }

    private synchronized void throttle(String host) {
        long now = System.currentTimeMillis();
        long wait = lastRequestAt.getOrDefault(host, 0L) + minIntervalMs - now;
        if (wait > 0) sleep(wait);
        lastRequestAt.put(host, System.currentTimeMillis());
    }

    private synchronized RobotsRules robotsFor(URI uri) {
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        CachedRobots cached = robotsCache.get(origin);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < ROBOTS_TTL_MS) return cached.rules;
        RobotsRules rules = RobotsRules.allowAll();
        try {
            throttle(uri.getHost().toLowerCase(Locale.ROOT));
            Connection.Response res = Jsoup.connect(origin + "/robots.txt").userAgent(USER_AGENT).timeout(timeoutMs)
                    .ignoreHttpErrors(true).ignoreContentType(true).followRedirects(true).execute();
            if (res.statusCode() == 200) rules = RobotsRules.parse(res.body(), AGENT_TOKEN);
        } catch (Exception e) {
            log.debug("robots.txt for {} unavailable ({}); treating as no restrictions", origin, e.getMessage());
        }
        robotsCache.put(origin, new CachedRobots(rules, System.currentTimeMillis()));
        return rules;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
