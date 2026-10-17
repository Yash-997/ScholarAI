package com.scholarship.service;

import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.SourceSnapshotRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds individual scheme pages instead of relying on homepages. Two strategies, both limited to pages robots.txt allows
 * (the fetcher enforces that):
 * <ul>
 *   <li>SITEMAP sources: read the sitemap (and the child sitemaps selected by the source's item_match), keep URLs that
 *       match link_filter, and register them as BRAND_PAGE sources read with the structured Buddy4Study adapter.</li>
 *   <li>Portals with discover=true: keep same-host anchors whose href or text matches link_filter and register them as
 *       SCHEME_PAGE sources (HTML adapter, never auto-applying).</li>
 * </ul>
 * Discovery only registers pages. It is idempotent (a URL is registered once) and never touches scheme records.
 */
@Service
public class SourceDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(SourceDiscoveryService.class);
    private static final Pattern LOC = Pattern.compile("<loc>\\s*([^<\\s]+)\\s*</loc>");
    private static final Pattern DOCUMENT = Pattern.compile("\\.(pdf|docx?|xlsx?|zip|jpe?g|png)(\\?|$)", Pattern.CASE_INSENSITIVE);

    public record Result(int found, int registered) {
    }

    private final SourceSnapshotRepository snapshots;
    private final PageFetcher fetcher;
    private final int maxDiscovered;

    public SourceDiscoveryService(SourceSnapshotRepository snapshots, PageFetcher fetcher,
                                  @Value("${app.ingestion.max-discovered-per-source:60}") int maxDiscovered) {
        this.snapshots = snapshots;
        this.fetcher = fetcher;
        this.maxDiscovered = maxDiscovered;
    }

    public Result discover(SourceSnapshotEntity src, PageFetcher.Page page) {
        List<String> urls = "SITEMAP".equals(src.getKind()) ? fromSitemap(src, page) : fromLinks(src, page);
        boolean structured = "SITEMAP".equals(src.getKind());
        Set<String> known = new HashSet<>();
        snapshots.findAll().forEach(s -> known.add(normalize(s.getUrl())));
        int registered = 0;
        for (String url : urls) {
            if (!known.add(normalize(url))) continue;
            SourceSnapshotEntity d = new SourceSnapshotEntity(idFor(url), url, src.getOrganization(), src.getPortalName() + " (discovered)");
            d.setKind(structured ? "BRAND_PAGE" : "SCHEME_PAGE");
            d.setAdapter(structured ? "B4S" : "HTML");
            d.setTrust(src.getTrust());
            d.setAutoApply(structured);          // structured partner data may refresh dates; scraped HTML never does
            d.setDiscovered(true);
            d.setDiscoveredFrom(src.getSourceId());
            snapshots.save(d);
            registered++;
        }
        log.info("Discovery from {}: {} candidate pages, {} newly registered", src.getSourceId(), urls.size(), registered);
        return new Result(urls.size(), registered);
    }

    private List<String> fromSitemap(SourceSnapshotEntity src, PageFetcher.Page page) {
        Pattern filter = src.getLinkFilter() == null ? null : Pattern.compile(src.getLinkFilter(), Pattern.CASE_INSENSITIVE);
        Pattern childFilter = src.getItemMatch() == null ? null : Pattern.compile(src.getItemMatch(), Pattern.CASE_INSENSITIVE);
        String host = hostOf(src.getUrl());
        List<String> locs = new ArrayList<>();
        String body = page.html() == null ? "" : page.html();
        if (body.contains("<sitemapindex")) {
            for (String child : locs(body)) {
                if (childFilter != null && !childFilter.matcher(child).find()) continue;
                if (!host.equals(hostOf(child))) continue;
                PageFetcher.Page p = fetcher.fetch(child);
                if (p.ok()) locs.addAll(locs(p.html()));
                else log.warn("Child sitemap {} not readable: {}", child, p.error());
            }
        } else {
            locs.addAll(locs(body));
        }
        List<String> out = new ArrayList<>();
        for (String u : locs) {
            if (!host.equals(hostOf(u)) || !u.contains("/page/")) continue;
            if (filter != null && !filter.matcher(u).find()) continue;
            out.add(u);
            if (out.size() >= maxDiscovered) break;
        }
        return out;
    }

    private List<String> fromLinks(SourceSnapshotEntity src, PageFetcher.Page page) {
        if (src.getLinkFilter() == null || page.html() == null) return List.of();
        Pattern filter = Pattern.compile(src.getLinkFilter(), Pattern.CASE_INSENSITIVE);
        String host = hostOf(src.getUrl());
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Element a : Jsoup.parse(page.html(), src.getUrl()).select("a[href]")) {
            String href = a.absUrl("href");
            if (!href.startsWith("http") || !host.equals(hostOf(href)) || DOCUMENT.matcher(href).find()) continue;
            if (!filter.matcher(href + " " + a.text()).find()) continue;
            out.add(href);
            if (out.size() >= maxDiscovered) break;
        }
        return new ArrayList<>(out);
    }

    private static List<String> locs(String xml) {
        List<String> out = new ArrayList<>();
        Matcher m = LOC.matcher(xml == null ? "" : xml);
        while (m.find()) out.add(m.group(1).trim());
        return out;
    }

    static String hostOf(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "";
        }
    }

    static String normalize(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://(www\\.)?", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static String idFor(String url) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(normalize(url).getBytes(StandardCharsets.UTF_8));
            return "D-" + HexFormat.of().formatHex(d).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
