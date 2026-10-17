package com.scholarship.service;

import com.scholarship.dto.ScholarshipDto;
import com.scholarship.entity.ScholarshipEntity;

import java.time.LocalDate;

/** Entity to API DTO, including the status computed for {@code today}. */
public final class ScholarshipMapper {

    private ScholarshipMapper() {
    }

    public static ScholarshipDto toDto(ScholarshipEntity e, LocalDate today) {
        SchemeStatusCalculator.Result status = SchemeStatusCalculator.compute(e, today);
        return new ScholarshipDto(
                e.getId(), e.getName(), e.getProvider(), e.getSchemeType(), e.getEligibility(),
                e.getIncomeLimit(), e.getMaximumFamilyIncome(), e.getMinimumMarksOrCgpa(),
                e.getCourse(), e.getState(), e.getCategoryCriteria(), e.getGenderRequirements(),
                e.getBenefits(), e.getDeadline(), e.getRequiredDocuments(), e.getApplicationUrl(), e.getSourceUrl(),
                e.getLastVerified(), e.getVerificationStatus(), e.getCreatedAt(), e.getUpdatedAt(),
                status.status().name(), status.note(), e.getOpenDate(), e.getDeadlineVerifiedOn(), e.getAcademicYear(),
                e.getLastChecked(), e.isNeedsReview(), e.getReviewReason(), e.getSourceId(),
                e.getWindowState(), e.getWindowEvidence(), e.getDeadlineSourceUrl(), e.getDeadlineFetchedAt(), e.getDeadlineOrigin());
    }
}
