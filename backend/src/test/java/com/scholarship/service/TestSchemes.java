package com.scholarship.service;

import com.scholarship.entity.ScholarshipEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Small builder so tests read as the rule they check. */
final class TestSchemes {

    private TestSchemes() {
    }

    static ScholarshipEntity scheme(long id, String name) {
        ScholarshipEntity s = new ScholarshipEntity();
        s.setId(id);
        s.setName(name);
        s.setProvider("Test Department");
        s.setState("ALL");
        s.setCategoryCriteria("ALL");
        s.setCourse("ALL");
        s.setGenderRequirements("ALL");
        s.setBenefits("Rs. 10,000 per year");
        s.setRequiredDocuments("Income Certificate;Marksheet");
        s.setApplicationUrl("https://example.gov.in/apply");
        s.setSourceUrl("https://example.gov.in/source");
        s.setVerificationStatus("VERIFIED");
        return s;
    }

    static ScholarshipEntity minMarks(ScholarshipEntity s, double marks) {
        s.setMinimumMarksOrCgpa(marks);
        return s;
    }

    static ScholarshipEntity maxIncome(ScholarshipEntity s, long income) {
        s.setMaximumFamilyIncome(BigDecimal.valueOf(income));
        return s;
    }

    static ScholarshipEntity dates(ScholarshipEntity s, LocalDate open, LocalDate deadline, LocalDate verifiedOn) {
        s.setOpenDate(open);
        s.setDeadline(deadline);
        s.setDeadlineVerifiedOn(verifiedOn);
        return s;
    }

    static EligibilityEvaluator.Profile profile(Long income, Double marks, String state, String category, String course, String gender) {
        return new EligibilityEvaluator.Profile(income == null ? null : BigDecimal.valueOf(income), marks,
                EligibilityEvaluator.normalizeState(state), EligibilityEvaluator.normalizeCategory(category),
                EligibilityEvaluator.normalizeCourse(course), EligibilityEvaluator.normalizeGender(gender));
    }
}
