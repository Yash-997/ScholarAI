package com.scholarship.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls student details out of free chat text with plain patterns. Anything it cannot read stays null ("not specified"),
 * so a missing detail is never guessed. Later texts override earlier ones.
 */
public final class ChatProfileExtractor {

    private static final List<String> STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura",
            "Uttar Pradesh", "Uttarakhand", "West Bengal", "Andaman and Nicobar Islands", "Chandigarh", "Delhi",
            "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private static final Pattern LAKH = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:lpa|lakhs?|lacs?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUPEES = Pattern.compile("(?:₹|rs\\.?|inr)\\s*([\\d,]{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern INCOME_NUMBER = Pattern.compile("income[^\\d]{0,25}([\\d,]{5,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT = Pattern.compile("(\\d{2,3}(?:\\.\\d+)?)\\s*(?:%|percent\\b|percentage\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARKS_WORD = Pattern.compile("(?:marks|scored|score|percentage|got)\\D{0,15}(\\d{2,3}(?:\\.\\d+)?)\\b(?!\\s*(?:lpa|lakh|lac))", Pattern.CASE_INSENSITIVE);
    private static final Pattern SC = Pattern.compile("\\bSC\\b");
    private static final Pattern ST = Pattern.compile("\\bST\\b");
    private static final Pattern GENERAL = Pattern.compile(
            "\\bgeneral\\s+(?:category|caste|quota|student)\\b|\\b(?:open|unreserved)\\s+category\\b|\\bcategory\\s*(?:is|:)?\\s*(?:general|open)\\b|\\bi\\s*(?:am|'m)\\s+(?:from\\s+)?(?:the\\s+)?general\\b");

    private ChatProfileExtractor() {
    }

    /** Overlays what can be read from each text, oldest first, onto {@code base} (which may hold nulls). */
    public static EligibilityEvaluator.Profile extract(EligibilityEvaluator.Profile base, List<String> texts) {
        BigDecimal income = base.income();
        Double marks = base.marks();
        String state = base.state(), category = base.category(), course = base.course(), gender = base.gender();
        for (String t : texts) {
            if (t == null || t.isBlank()) continue;
            BigDecimal i = income(t);
            if (i != null) income = i;
            Double m = marks(t);
            if (m != null) marks = m;
            String st = state(t);
            if (st != null) state = st;
            String c = category(t);
            if (c != null) category = c;
            String co = course(t);
            if (co != null) course = co;
            String g = gender(t);
            if (g != null) gender = g;
        }
        return new EligibilityEvaluator.Profile(income, marks, state, category, course, gender);
    }

    static BigDecimal income(String t) {
        Matcher m = LAKH.matcher(t);
        if (m.find()) return new BigDecimal(m.group(1)).multiply(BigDecimal.valueOf(100000)).stripTrailingZeros().max(BigDecimal.ZERO);
        m = RUPEES.matcher(t);
        if (m.find()) return parseAmount(m.group(1));
        m = INCOME_NUMBER.matcher(t);
        if (m.find()) return parseAmount(m.group(1));
        return null;
    }

    private static BigDecimal parseAmount(String digits) {
        try {
            return new BigDecimal(digits.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    static Double marks(String t) {
        Matcher m = PERCENT.matcher(t);
        Double found = null;
        while (m.find()) {
            // "40% disability" is a disability percentage, not marks.
            String after = t.substring(m.end(), Math.min(t.length(), m.end() + 14)).toLowerCase(Locale.ROOT);
            if (after.stripLeading().startsWith("disab")) continue;
            found = Double.valueOf(m.group(1));
            break;
        }
        if (found == null) {
            m = MARKS_WORD.matcher(t);
            if (m.find()) found = Double.valueOf(m.group(1));
        }
        return found != null && found >= 0 && found <= 100 ? found : null;
    }

    static String state(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        // Longest names first so "Madhya Pradesh" is not shadowed by a shorter match.
        String best = null;
        for (String s : STATES) {
            if (Pattern.compile("\\b" + Pattern.quote(s.toLowerCase(Locale.ROOT)) + "\\b").matcher(lower).find()
                    && (best == null || s.length() > best.length())) {
                best = s;
            }
        }
        return best;
    }

    static String category(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        // SC / ST are matched in capitals only, so the words "sc" or "st" inside ordinary text do not count.
        if (SC.matcher(t).find() || lower.contains("scheduled caste")) return "SC";
        if (ST.matcher(t).find() || lower.contains("scheduled tribe")) return "ST";
        if (lower.matches("(?s).*\\bobc\\b.*|(?s).*other backward.*")) return "OBC";
        if (lower.matches("(?s).*\\b(vjnt|dnt|sbc|nt-?[abcd])\\b.*")) return "VJNT";
        if (lower.matches("(?s).*\\bews\\b.*")) return "EWS";
        if (lower.matches("(?s).*\\b(minority|muslim|christian|sikh|buddhist|jain|parsi)\\b.*")) return "MINORITY";
        if (lower.matches("(?s).*\\b(pwd|disabled|disability|specially[- ]abled|divyang)\\b.*")) return "PWD";
        if (lower.matches("(?s).*\\b(orphan|orphaned)\\b.*")) return "ORPHAN";
        if (GENERAL.matcher(lower).find()) return "GEN";
        return null;
    }

    static String gender(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*\\b(girl|girls|female|woman|women|daughter)\\b.*")) return "Female";
        if (lower.matches("(?s).*\\b(boy|boys|male|man|men|son)\\b.*")) return "Male";
        return null;
    }

    static String course(String t) {
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*\\bdiploma\\b.*")) return "Diploma";
        if (lower.matches("(?s).*(\\bb\\.?\\s?tech\\b|\\bb\\.?e\\.?\\b|engineering|\\bengg\\b).*")) return "Engineering";
        if (lower.matches("(?s).*(\\bmbbs\\b|medical|\\bbds\\b).*")) return "Medical";
        if (lower.matches("(?s).*(pharm).*")) return "Pharmacy";
        if (lower.matches("(?s).*(\\bb\\.?sc\\b|\\bb\\.?com\\b|\\bb\\.?a\\b|\\bbba\\b|\\bbca\\b|degree|graduation).*")) return "Degree";
        return null;
    }
}
