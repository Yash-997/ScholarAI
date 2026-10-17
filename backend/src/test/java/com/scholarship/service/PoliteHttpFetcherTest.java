package com.scholarship.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real fetcher against a local HTTP server: retries, robots.txt, challenges and authentication. */
class PoliteHttpFetcherTest {

    private HttpServer server;
    private String base;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final AtomicInteger flakyCalls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        route("/ok", 200, "<html><body><h1>Scheme</h1><p>Last date 26 October 2026</p></body></html>");
        route("/missing", 404, "not found");
        route("/auth", 401, "login required");
        route("/challenge", 403, "<html><title>Just a moment...</title><body>Checking your browser</body></html>");
        route("/captcha", 200, "<html><body>Please solve this CAPTCHA to continue</body></html>");
        route("/private/secret", 200, "<html><body>secret</body></html>");
        route("/robots.txt", 200, "User-agent: *\nDisallow: /private/\n");
        server.createContext("/flaky", ex -> {
            hits.computeIfAbsent("/flaky", k -> new AtomicInteger()).incrementAndGet();
            boolean fail = flakyCalls.incrementAndGet() <= 2;
            byte[] b = (fail ? "busy" : "<html><body>recovered</body></html>").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(fail ? 503 : 200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void route(String path, int status, String body) {
        server.createContext(path, ex -> {
            hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PoliteHttpFetcher fetcher(int attempts) {
        return new PoliteHttpFetcher(3000, attempts, 0, 1, true);
    }

    @Test
    void successReturnsTextAndRawHtml() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/ok");
        assertTrue(p.ok());
        assertEquals(200, p.status());
        assertTrue(p.text().contains("Last date 26 October 2026"));
        assertTrue(p.html().contains("<h1>Scheme</h1>"));
    }

    @Test
    void retriesTransientServerErrorsThenSucceeds() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/flaky");
        assertTrue(p.ok(), p.error());
        assertEquals(3, hits.get("/flaky").get());
    }

    @Test
    void givesUpAfterTheConfiguredAttempts() {
        flakyCalls.set(-100);   // always fails
        PageFetcher.Page p = fetcher(2).fetch(base + "/flaky");
        assertFalse(p.ok());
        assertEquals(503, p.status());
        assertEquals(2, hits.get("/flaky").get());
    }

    @Test
    void clientErrorsAreNotRetried() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/missing");
        assertFalse(p.ok());
        assertEquals(404, p.status());
        assertEquals(1, hits.get("/missing").get());
    }

    @Test
    void robotsDisallowedPathsAreNeverRequested() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/private/secret");
        assertFalse(p.ok());
        assertTrue(p.error().startsWith("ROBOTS_DISALLOWED"));
        assertNull(hits.get("/private/secret"), "the page itself must not be requested");
    }

    @Test
    void botChallengesAreReportedAsBlocked_andNotRetriedOrBypassed() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/challenge");
        assertFalse(p.ok());
        assertTrue(p.error().startsWith("BLOCKED"), p.error());
        assertEquals(1, hits.get("/challenge").get());
        PageFetcher.Page c = fetcher(3).fetch(base + "/captcha");
        assertFalse(c.ok());
        assertTrue(c.error().startsWith("BLOCKED"), c.error());
    }

    @Test
    void loginWallsAreReportedAndNotAttempted() {
        PageFetcher.Page p = fetcher(3).fetch(base + "/auth");
        assertFalse(p.ok());
        assertTrue(p.error().startsWith("AUTH_REQUIRED"));
        assertEquals(1, hits.get("/auth").get());
    }

    @Test
    void connectionFailuresAreReturnedNotThrown() {
        server.stop(0);
        PageFetcher.Page p = fetcher(2).fetch(base + "/ok");
        assertFalse(p.ok());
        assertNull(p.status());
    }

    @Test
    void invalidUrlsAreRejected() {
        assertFalse(fetcher(1).fetch("not a url").ok());
        assertFalse(fetcher(1).fetch("ftp://example.org/x").ok());
    }

    @Test
    void requestsToOneHostAreSpaced() {
        PoliteHttpFetcher slow = new PoliteHttpFetcher(3000, 1, 150, 1, false);
        long t0 = System.nanoTime();
        slow.fetch(base + "/ok");
        slow.fetch(base + "/ok");
        slow.fetch(base + "/ok");
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(ms >= 290, "three requests with a 150 ms interval took only " + ms + " ms");
    }

    // ---- robots.txt interpretation -------------------------------------------------------------

    @Test
    void robotsRulesFollowLongestMatchAndIgnoreHtmlShells() {
        RobotsRules r = RobotsRules.parse("User-agent: *\nDisallow: /media-url/*\nDisallow: /UID/*\nDisallow: /a/\nAllow: /a/public/\n", "scholarai");
        assertTrue(r.allows("/page/x"));
        assertFalse(r.allows("/media-url/abc"));
        assertFalse(r.allows("/a/secret"));
        assertTrue(r.allows("/a/public/doc"));
        assertTrue(RobotsRules.parse("<!DOCTYPE html><html>SPA shell</html>", "scholarai").allows("/anything"));
        assertTrue(RobotsRules.parse("User-agent: *\nDisallow:\n", "scholarai").allows("/anything"));
        assertFalse(RobotsRules.parse("User-agent: ScholarAI\nDisallow: /\n\nUser-agent: *\nAllow: /\n", "scholarai").allows("/x"),
                "a group naming us wins over the wildcard group");
        assertFalse(RobotsRules.parse("User-agent: *\nDisallow: /*login\n", "scholarai").allows("/web/login"));
    }
}
