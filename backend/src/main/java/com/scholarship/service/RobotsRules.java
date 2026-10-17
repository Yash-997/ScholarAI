package com.scholarship.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal robots.txt interpreter: the group for our user-agent token, else the "*" group; longest matching rule wins,
 * Allow beats Disallow on a tie. A missing file, or a body that is not robots.txt (many single-page apps answer every
 * path with their HTML shell), means no restrictions.
 */
public final class RobotsRules {

    private record Rule(boolean allow, String path) {
    }

    private final List<Rule> rules;

    private RobotsRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static RobotsRules allowAll() {
        return new RobotsRules(List.of());
    }

    public static RobotsRules parse(String body, String agentToken) {
        if (body == null || body.isBlank() || body.stripLeading().startsWith("<")) return allowAll();
        String token = agentToken.toLowerCase(Locale.ROOT);
        List<Rule> specific = new ArrayList<>();
        List<Rule> wildcard = new ArrayList<>();
        boolean matchedSpecific = false;
        List<String> agents = new ArrayList<>();
        boolean lastWasAgent = false;
        for (String raw : body.split("\\R")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                if (!lastWasAgent) agents = new ArrayList<>();
                agents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (!key.equals("allow") && !key.equals("disallow")) continue;
            if (value.isEmpty()) continue;   // "Disallow:" with no path allows everything
            Rule rule = new Rule(key.equals("allow"), value);
            if (agents.stream().anyMatch(a -> !a.equals("*") && token.contains(a))) {
                specific.add(rule);
                matchedSpecific = true;
            } else if (agents.contains("*")) {
                wildcard.add(rule);
            }
        }
        return new RobotsRules(matchedSpecific ? specific : wildcard);
    }

    public boolean allows(String pathAndQuery) {
        String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
        Rule best = null;
        for (Rule r : rules) {
            if (matches(r.path, path) && (best == null || r.path.length() > best.path.length()
                    || (r.path.length() == best.path.length() && r.allow))) {
                best = r;
            }
        }
        return best == null || best.allow;
    }

    /** Supports the "*" wildcard and the "$" end anchor used by major crawlers. */
    private static boolean matches(String pattern, String path) {
        boolean anchored = pattern.endsWith("$");
        String p = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        StringBuilder rx = new StringBuilder("^");
        for (char c : p.toCharArray()) {
            if (c == '*') rx.append(".*");
            else rx.append(java.util.regex.Pattern.quote(String.valueOf(c)));
        }
        if (anchored) rx.append('$');
        return java.util.regex.Pattern.compile(rx.toString()).matcher(path).find();
    }
}
