package com.scholarship.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks applied to model text before it reaches a student. */
public final class ReplyGuard {

    public static final String OMITTED = "(link omitted: not an official source on record)";
    private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"'<]+", Pattern.CASE_INSENSITIVE);

    private ReplyGuard() {
    }

    /** Replaces every URL that is not one of {@code allowed} (compared case-insensitively, ignoring "www." and a trailing slash). */
    public static String stripUnknownUrls(String reply, Set<String> allowed) {
        Set<String> norm = new HashSet<>();
        allowed.forEach(u -> norm.add(normalize(u)));
        Matcher m = URL.matcher(reply);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String found = m.group();
            // Models wrap links in Markdown emphasis or punctuation; those characters are not part of the URL.
            String trimmed = found.replaceAll("[.,;:!?*_~`]+$", "");
            String tail = found.substring(trimmed.length());
            m.appendReplacement(out, Matcher.quoteReplacement(norm.contains(normalize(trimmed)) ? found : OMITTED + tail));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** False for blank, tiny, or raw-JSON/error-looking output, which is not a usable answer. */
    public static boolean looksUsable(String reply) {
        if (reply == null) return false;
        String t = reply.trim();
        if (t.length() < 20) return false;
        String lower = t.toLowerCase(Locale.ROOT);
        return !(t.startsWith("{") && (lower.contains("\"error\"") || lower.contains("\"message\"")));
    }

    static String normalize(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
