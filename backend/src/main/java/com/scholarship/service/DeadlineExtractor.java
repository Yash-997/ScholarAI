package com.scholarship.service;

import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds dates that appear next to deadline wording in page text. The result is only ever a <em>candidate</em> for a
 * human to review; it is never written to a scheme's live deadline.
 */
public final class DeadlineExtractor {

    private static final Pattern KEYWORD = Pattern.compile(
            "last\\s+date|last\\s+day|closing\\s+date|deadline|due\\s+date|apply\\s+(?:by|before|till|until)|applications?\\s+(?:close|closes|closing)|(?:till|until|before)\\s+(?=\\d)",
            Pattern.CASE_INSENSITIVE);

    private static final String MONTHS = "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-]+(" + MONTHS + ")\\.?,?[\\s\\-]+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile("\\b(" + MONTHS + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_DMY = Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4})\\b");
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");

    private static final Map<String, Month> MONTH_BY_PREFIX = Map.ofEntries(
            Map.entry("jan", Month.JANUARY), Map.entry("feb", Month.FEBRUARY), Map.entry("mar", Month.MARCH),
            Map.entry("apr", Month.APRIL), Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("aug", Month.AUGUST), Map.entry("sep", Month.SEPTEMBER),
            Map.entry("oct", Month.OCTOBER), Map.entry("nov", Month.NOVEMBER), Map.entry("dec", Month.DECEMBER));

    private static final int WINDOW = 90;

    private DeadlineExtractor() {
    }

    /** Dates within {@link #WINDOW} characters after deadline wording, restricted to [today-1y, today+2y], sorted. */
    public static TreeSet<LocalDate> extract(String text, LocalDate today) {
        return new TreeSet<>(withEvidence(text, today).keySet());
    }

    /** Same as {@link #extract} but keeps, for each date, the wording it was found next to (for an audit trail). */
    public static java.util.TreeMap<LocalDate, String> withEvidence(String text, LocalDate today) {
        java.util.TreeMap<LocalDate, String> out = new java.util.TreeMap<>();
        if (text == null || text.isBlank()) return out;
        LocalDate min = today.minusYears(1);
        LocalDate max = today.plusYears(2);
        Matcher k = KEYWORD.matcher(text);
        while (k.find()) {
            int from = k.end();
            String window = text.substring(from, Math.min(text.length(), from + WINDOW));
            TreeSet<LocalDate> found = new TreeSet<>();
            collect(window, found, min, max);
            String snippet = text.substring(k.start(), Math.min(text.length(), from + WINDOW)).replaceAll("\\s+", " ").trim();
            for (LocalDate d : found) out.putIfAbsent(d, snippet);
        }
        return out;
    }

    /** Only the date nearest to the deadline wording counts; later dates in the window belong to other sentences. */
    private static void collect(String w, TreeSet<LocalDate> out, LocalDate min, LocalDate max) {
        TreeSet<LocalDate> all = new TreeSet<>();
        java.util.TreeMap<Integer, LocalDate> byPosition = new java.util.TreeMap<>();
        Matcher m = DAY_MONTH_YEAR.matcher(w);
        while (m.find()) put(byPosition, m.start(), min, max, Integer.parseInt(m.group(3)), month(m.group(2)), Integer.parseInt(m.group(1)));
        m = MONTH_DAY_YEAR.matcher(w);
        while (m.find()) put(byPosition, m.start(), min, max, Integer.parseInt(m.group(3)), month(m.group(1)), Integer.parseInt(m.group(2)));
        m = NUMERIC_DMY.matcher(w);
        while (m.find()) put(byPosition, m.start(), min, max, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        m = ISO.matcher(w);
        while (m.find()) put(byPosition, m.start(), min, max, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        if (!byPosition.isEmpty()) out.add(byPosition.firstEntry().getValue());
    }

    private static void put(java.util.TreeMap<Integer, LocalDate> byPosition, int pos, LocalDate min, LocalDate max, int year, int month, int day) {
        try {
            LocalDate d = LocalDate.of(year, month, day);
            if (!d.isBefore(min) && !d.isAfter(max)) byPosition.putIfAbsent(pos, d);
        } catch (Exception ignored) {
            // impossible calendar date (e.g. 31/02) - not a date
        }
    }

    private static int month(String name) {
        return MONTH_BY_PREFIX.get(name.substring(0, 3).toLowerCase(Locale.ROOT)).getValue();
    }
}
