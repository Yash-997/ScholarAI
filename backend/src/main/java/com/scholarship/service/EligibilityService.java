package com.scholarship.service;

import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies {@link EligibilityEvaluator} to the VERIFIED schemes and attaches the computed status. This is the single
 * source of truth for "who is eligible"; the recommendation API and the chatbot both call it, and no model is involved.
 */
@Service
public class EligibilityService {

    private static final Set<String> WILDCARDS = Set.of("all", "all india", "all categories", "all courses", "any", "");

    /** @param why the concrete criteria that were checked and passed, in plain words */
    public record Match(ScholarshipEntity scheme, SchemeStatusCalculator.Result status, List<String> why) {
    }

    private final ScholarshipRepository repository;
    private final Clock clock;

    public EligibilityService(ScholarshipRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Eligible schemes, ordered by id. Schemes whose verified deadline has passed are included (the student may want to
     * know about next cycle) but carry status CLOSED; callers decide how to present them.
     */
    @Transactional(readOnly = true)
    public List<Match> match(EligibilityEvaluator.Profile profile) {
        LocalDate today = LocalDate.now(clock);
        List<Match> out = new ArrayList<>();
        for (ScholarshipEntity s : repository.findByVerificationStatus("VERIFIED")) {
            if (EligibilityEvaluator.isEligible(s, profile)) {
                out.add(new Match(s, SchemeStatusCalculator.compute(s, today), explain(s, profile)));
            }
        }
        out.sort(Comparator.comparing(m -> m.scheme().getId()));
        return out;
    }

    /** Presentation order: usable schemes first, CLOSED last. Stable, so incoming (relevance) order is kept inside each group. */
    public static int statusRank(SchemeStatus status) {
        return switch (status) {
            case OPEN -> 0;
            case UPCOMING -> 1;
            case UNKNOWN -> 2;
            case UNVERIFIED -> 3;
            case CLOSED -> 4;
        };
    }

    static List<String> explain(ScholarshipEntity s, EligibilityEvaluator.Profile p) {
        List<String> why = new ArrayList<>();
        if (p.income() != null && s.getMaximumFamilyIncome() != null) {
            why.add("family income " + rupees(p.income()) + " is within the " + rupees(s.getMaximumFamilyIncome()) + " ceiling");
        }
        if (p.marks() != null && s.getMinimumMarksOrCgpa() != null) {
            why.add("marks " + trim(p.marks()) + "% meet the " + trim(s.getMinimumMarksOrCgpa()) + "% minimum");
        }
        if (p.state() != null && restricts(s.getState())) {
            why.add("domicile " + p.state() + " is covered");
        }
        if (p.category() != null && restricts(s.getCategoryCriteria())) {
            why.add("category " + p.category() + " is covered");
        }
        if (p.course() != null && restricts(s.getCourse())) {
            why.add("course " + p.course() + " is covered");
        }
        if (p.gender() != null && restricts(s.getGenderRequirements())) {
            why.add("gender " + p.gender() + " is covered");
        }
        return why;
    }

    private static boolean restricts(String column) {
        if (column == null || column.isBlank()) return false;
        for (String t : column.split("[;,|/]")) {
            if (WILDCARDS.contains(t.trim().toLowerCase(Locale.ROOT))) return false;
        }
        return true;
    }

    private static String rupees(BigDecimal v) {
        return "Rs. " + String.format(Locale.ENGLISH, "%,d", v.longValue());
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
