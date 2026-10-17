package com.scholarship.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the structured programme data Buddy4Study (the application partner of many corporate programmes) embeds in its
 * pages as Next.js JSON: {@code props.pageProps.scholarship.brandPage.scholarships[]}. Each element is one programme
 * cycle with its own deadline, launch date, eligibility, benefits, documents and application link, which is far more
 * reliable than guessing dates out of prose. A page that has no such data (unknown slug, retired programme) yields an
 * empty list, never invented values.
 */
public final class BuddyBrandExtractor {

    private static final Pattern NEXT_DATA = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern YEAR_IN_TITLE = Pattern.compile("(20\\d\\d)\\s*[\\u2013\\-/]\\s*(\\d{2,4})");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BuddyBrandExtractor() {
    }

    public static List<SchemeCandidate> extract(String html, String pageUrl) {
        List<SchemeCandidate> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = NEXT_DATA.matcher(html);
        if (!m.find()) return out;
        JsonNode items;
        try {
            items = MAPPER.readTree(m.group(1)).path("props").path("pageProps").path("scholarship").path("brandPage").path("scholarships");
        } catch (Exception e) {
            return out;
        }
        if (!items.isArray()) return out;
        for (JsonNode item : items) {
            String title = clean(item.path("title").asText(""));
            if (title.isEmpty()) continue;
            LocalDate deadline = date(item.path("deadline").asText(null));
            LocalDate launch = date(item.path("applicationLaunchDate").asText(null));
            String id = item.path("scholarshipId").asText("");
            String evidence = "Buddy4Study programme data, scholarshipId " + id + ": deadline=" + (deadline == null ? "none" : deadline)
                    + ", applicationLaunchDate=" + (launch == null ? "none" : launch) + ", title=\"" + title + "\"";
            out.add(new SchemeCandidate(id.isEmpty() ? null : "b4s:" + id, title, academicYear(title, item.path("session").asText(null)),
                    launch, deadline, clean(item.path("eligibility").asText("")), clean(item.path("benefits").asText("")),
                    blankToNull(item.path("applyLink").asText("")), clean(item.path("requiredDocument").asText("")),
                    evidence, null, null, pageUrl));
        }
        return out;
    }

    static String academicYear(String title, String session) {
        Matcher m = YEAR_IN_TITLE.matcher(title);
        if (m.find()) {
            String end = m.group(2);
            return m.group(1) + "-" + (end.length() == 4 ? end.substring(2) : end);
        }
        return session == null || session.isBlank() || "null".equals(session) ? null : session;
    }

    private static LocalDate date(String v) {
        try {
            return v == null || v.isBlank() || "null".equals(v) ? null : LocalDate.parse(v.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String clean(String html) {
        return html == null ? "" : Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() || "null".equals(v) ? null : v.trim();
    }
}
