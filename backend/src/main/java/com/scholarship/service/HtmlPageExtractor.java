package com.scholarship.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts what an ordinary HTML scheme page states: title, academic year, application link, dates written next to
 * deadline wording, and explicit open/closed notices. Pages that merely list many schemes produce several conflicting
 * dates; the refresh policy refuses to apply those and flags them instead.
 */
public final class HtmlPageExtractor {

    private static final Pattern YEAR = Pattern.compile("\\b(20\\d\\d)\\s*[\\u2013\\-/]\\s*(\\d{2,4})\\b");
    private static final Pattern CLOSED = Pattern.compile(
            "applications?[^.]{0,90}?\\b(?:are|is|have been|has been)\\s+(?:now\\s+)?closed\\b|\\bapplications?\\s+closed\\b|\\bregistrations?\\s+(?:are\\s+)?(?:now\\s+)?closed\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLLING = Pattern.compile(
            "rolling basis|round the year|throughout the year|open all year|year[- ]round|all year round", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPLY_LINK = Pattern.compile("apply|application|register", Pattern.CASE_INSENSITIVE);

    private HtmlPageExtractor() {
    }

    /** At most one candidate: a page is treated as describing a single scheme. */
    public static List<SchemeCandidate> extract(String html, String text, String pageUrl, LocalDate today) {
        List<SchemeCandidate> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        Document doc = Jsoup.parse(html == null ? "" : html, pageUrl);

        String name = doc.select("h1").isEmpty() ? doc.title() : doc.select("h1").first().text();
        name = name == null ? "" : name.replaceAll("\\s+[|\\u2013-]\\s+[^|\\u2013-]*$", "").trim();

        Matcher y = YEAR.matcher(text);
        String year = null;
        if (y.find()) {
            String end = y.group(2);
            year = y.group(1) + "-" + (end.length() == 4 ? end.substring(2) : end);
        }

        TreeMap<LocalDate, String> dates = DeadlineExtractor.withEvidence(text, today);
        LocalDate deadline = null;
        String evidence = null;
        if (dates.size() == 1) {
            Map.Entry<LocalDate, String> e = dates.firstEntry();
            deadline = e.getKey();
            evidence = "Page text: \"" + e.getValue() + "\"";
        } else if (dates.size() > 1) {
            evidence = "AMBIGUOUS: several dates near deadline wording: " + dates.keySet();
        }

        String windowState = null;
        String windowEvidence = null;
        Matcher closed = CLOSED.matcher(text);
        Matcher rolling = ROLLING.matcher(text);
        boolean isClosed = closed.find();
        boolean isRolling = rolling.find();
        if (isClosed && !isRolling) {
            windowState = SchemeStatusCalculator.CLOSED_NOTICE;
            windowEvidence = "Page text: \"" + snippet(text, closed.start(), closed.end()) + "\"";
        } else if (isRolling && !isClosed) {
            windowState = SchemeStatusCalculator.ROLLING_OPEN;
            windowEvidence = "Page text: \"" + snippet(text, rolling.start(), rolling.end()) + "\"";
        }

        String apply = null;
        for (var a : doc.select("a[href]")) {
            if (APPLY_LINK.matcher(a.text()).find() && a.absUrl("href").startsWith("http")) {
                apply = a.absUrl("href");
                break;
            }
        }
        out.add(new SchemeCandidate(null, name, year, null, deadline, null, null, apply, null, evidence, windowState, windowEvidence, pageUrl));
        return out;
    }

    private static String snippet(String text, int start, int end) {
        return text.substring(Math.max(0, start - 20), Math.min(text.length(), end + 40)).replaceAll("\\s+", " ").trim();
    }
}
