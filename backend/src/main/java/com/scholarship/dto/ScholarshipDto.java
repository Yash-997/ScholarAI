package com.scholarship.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ScholarshipDto(
        Long id,

        @NotBlank(message = "Scholarship name is required")
        String name,

        String provider,

        @JsonProperty("scheme_type")
        @JsonAlias("schemeType")
        String schemeType,

        String eligibility,

        @PositiveOrZero(message = "Income limit must be zero or positive")
        @JsonProperty("income_limit")
        @JsonAlias({"incomeLimit", "maximum_family_income", "maximumFamilyIncome"})
        BigDecimal incomeLimit,

        @JsonProperty("maximum_family_income")
        @JsonAlias("maximumFamilyIncome")
        BigDecimal maximumFamilyIncome,

        @JsonProperty("minimum_marks_or_cgpa")
        @JsonAlias("minimumMarksOrCgpa")
        Double minimumMarksOrCgpa,

        String course,
        String state,

        @JsonProperty("category_criteria")
        @JsonAlias("categoryCriteria")
        String categoryCriteria,

        @JsonProperty("gender_requirements")
        @JsonAlias("genderRequirements")
        String genderRequirements,

        String benefits,
        LocalDate deadline,

        @JsonProperty("required_documents")
        @JsonAlias("requiredDocuments")
        String requiredDocuments,

        @JsonProperty("application_url")
        @JsonAlias("applicationUrl")
        String applicationUrl,

        @JsonProperty("source_url")
        @JsonAlias("sourceUrl")
        String sourceUrl,

        @JsonProperty("last_verified")
        @JsonAlias("lastVerified")
        LocalDate lastVerified,

        @JsonProperty("verification_status")
        @JsonAlias("verificationStatus")
        String verificationStatus,

        @JsonProperty("created_at")
        OffsetDateTime createdAt,

        @JsonProperty("updated_at")
        OffsetDateTime updatedAt,

        /** Computed from verified dates and today's date; ignored on input. */
        @JsonProperty(value = "status", access = JsonProperty.Access.READ_ONLY)
        String status,

        @JsonProperty(value = "status_note", access = JsonProperty.Access.READ_ONLY)
        String statusNote,

        @JsonProperty("open_date")
        LocalDate openDate,

        @JsonProperty("deadline_verified_on")
        LocalDate deadlineVerifiedOn,

        @JsonProperty("academic_year")
        String academicYear,

        @JsonProperty(value = "last_checked", access = JsonProperty.Access.READ_ONLY)
        OffsetDateTime lastChecked,

        @JsonProperty(value = "needs_review", access = JsonProperty.Access.READ_ONLY)
        Boolean needsReview,

        @JsonProperty(value = "review_reason", access = JsonProperty.Access.READ_ONLY)
        String reviewReason,

        @JsonProperty(value = "source_id", access = JsonProperty.Access.READ_ONLY)
        String sourceId,

        @JsonProperty(value = "window_state", access = JsonProperty.Access.READ_ONLY)
        String windowState,

        @JsonProperty(value = "window_evidence", access = JsonProperty.Access.READ_ONLY)
        String windowEvidence,

        @JsonProperty(value = "deadline_source_url", access = JsonProperty.Access.READ_ONLY)
        String deadlineSourceUrl,

        @JsonProperty(value = "deadline_fetched_at", access = JsonProperty.Access.READ_ONLY)
        OffsetDateTime deadlineFetchedAt,

        @JsonProperty(value = "deadline_origin", access = JsonProperty.Access.READ_ONLY)
        String deadlineOrigin
) {
    public ScholarshipDto(
            Long id,
            String name,
            String provider,
            String schemeType,
            String eligibility,
            BigDecimal incomeLimit,
            String course,
            String state,
            String categoryCriteria,
            String benefits,
            LocalDate deadline,
            String requiredDocuments,
            String applicationUrl,
            String sourceUrl,
            LocalDate lastVerified,
            String verificationStatus,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(
                id, name, provider, schemeType, eligibility,
                incomeLimit, incomeLimit, null,
                course, state, categoryCriteria, null, benefits,
                deadline, requiredDocuments, applicationUrl, sourceUrl,
                lastVerified, verificationStatus, createdAt, updatedAt,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );
    }

    public BigDecimal resolvedIncomeLimit() {
        return maximumFamilyIncome != null ? maximumFamilyIncome : incomeLimit;
    }
}
