package com.scholarship.service;

import com.scholarship.dto.RecommendRequestDto;
import com.scholarship.entity.ScholarshipEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic eligibility rules. No database, no model, no clock: given a scheme and a student profile it returns
 * the list of criteria the student fails (empty = eligible). Nothing in here is generated or ranked by AI.
 *
 * Rules (all boundaries inclusive):
 * <ul>
 *   <li>income: student income &lt;= scheme maximum family income</li>
 *   <li>marks: student marks &gt;= scheme minimum marks (so lowering marks can only remove schemes)</li>
 *   <li>state / category / course / gender: the student's value must be one of the scheme's ';' ',' '|' '/' separated
 *       tokens, or the scheme must list a wildcard (ALL, All India, Any, All Categories)</li>
 * </ul>
 * A profile field that is null is "not specified" and is not filtered on. A scheme field that is null means unrestricted.
 */
public final class EligibilityEvaluator {

    private static final Set<String> WILDCARDS = Set.of("all", "all india", "all categories", "all courses", "any", "all genders", "");

    private EligibilityEvaluator() {
    }

    /** Normalised student profile. */
    public record Profile(BigDecimal income, Double marks, String state, String category, String course, String gender) {

        public static Profile of(RecommendRequestDto r) {
            return new Profile(r.annualFamilyIncome(), r.marks(), normalizeState(r.state()), normalizeCategory(r.category()),
                    normalizeCourse(r.course()), normalizeGender(r.gender()));
        }
    }

    public static boolean isEligible(ScholarshipEntity s, Profile p) {
        return failures(s, p).isEmpty();
    }

    /** Human-readable list of failed criteria; empty when the student is eligible. */
    public static List<String> failures(ScholarshipEntity s, Profile p) {
        List<String> out = new ArrayList<>();
        BigDecimal maxIncome = s.getMaximumFamilyIncome();
        if (p.income() != null && maxIncome != null && p.income().compareTo(maxIncome) > 0) {
            out.add("family income " + p.income().toPlainString() + " is above the " + maxIncome.toPlainString() + " ceiling");
        }
        Double minMarks = s.getMinimumMarksOrCgpa();
        if (p.marks() != null && minMarks != null && p.marks() < minMarks) {
            out.add("marks " + p.marks() + " are below the " + minMarks + " minimum");
        }
        if (p.state() != null && !tokenMatch(s.getState(), p.state(), EligibilityEvaluator::normalizeStateToken)) {
            out.add("scheme is limited to " + s.getState());
        }
        if (p.category() != null && !tokenMatch(s.getCategoryCriteria(), p.category(), EligibilityEvaluator::normalizeCategory)) {
            out.add("scheme is limited to categories " + s.getCategoryCriteria());
        }
        if (p.course() != null && !tokenMatch(s.getCourse(), p.course(), EligibilityEvaluator::normalizeCourse)) {
            out.add("scheme is limited to courses " + s.getCourse());
        }
        if (p.gender() != null && !tokenMatch(s.getGenderRequirements(), p.gender(), EligibilityEvaluator::normalizeGender)) {
            out.add("scheme is limited to gender " + s.getGenderRequirements());
        }
        return out;
    }

    private static boolean tokenMatch(String schemeValue, String studentValue, java.util.function.Function<String, String> norm) {
        if (schemeValue == null || schemeValue.isBlank()) return true;
        for (String raw : schemeValue.split("[;,|/]")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (WILDCARDS.contains(token)) return true;
            String n = norm.apply(raw.trim());
            if (n != null && n.equalsIgnoreCase(studentValue)) return true;
        }
        return false;
    }

    // ---- normalisers (shared with ScholarshipRagService; the form values map onto dataset tokens) ----

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String normalizeStateToken(String s) {
        return s == null ? null : s.trim();
    }

    public static String normalizeState(String state) {
        String s = blankToNull(state);
        return (s == null || s.equalsIgnoreCase("all india") || s.equalsIgnoreCase("all")) ? null : s;
    }

    public static String normalizeGender(String gender) {
        String g = blankToNull(gender);
        if (g == null) return null;
        String l = g.toLowerCase(Locale.ROOT);
        if (l.startsWith("all") || l.startsWith("any")) return null;
        if (l.startsWith("f") || l.startsWith("girl") || l.startsWith("wom")) return "Female";
        if (l.startsWith("m") || l.startsWith("boy")) return "Male";
        return g;
    }

    public static String normalizeCategory(String category) {
        String c = blankToNull(category);
        if (c == null || c.equalsIgnoreCase("all")) return null;
        String l = c.toLowerCase(Locale.ROOT);
        if (l.startsWith("gen") || l.startsWith("open")) return "GEN";
        if (l.startsWith("vjnt") || l.startsWith("dnt") || l.startsWith("sbc")) return "VJNT";
        if (l.startsWith("minor")) return "MINORITY";
        if (l.startsWith("orphan")) return "ORPHAN";
        return c.toUpperCase(Locale.ROOT);
    }

    public static String normalizeCourse(String course) {
        String c = blankToNull(course);
        if (c == null || c.equalsIgnoreCase("all")) return null;
        String l = c.toLowerCase(Locale.ROOT);
        if (l.contains("diploma")) return "Diploma";
        if (l.contains("tech") || l.contains("eng") || l.startsWith("b.e")) return "Engineering";
        if (l.contains("mbbs") || l.contains("medic")) return "Medical";
        if (l.contains("pharm")) return "Pharmacy";
        return "Degree";
    }
}
