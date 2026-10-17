package com.scholarship.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ScholarshipIngestionDto(
        Long id,

        @NotBlank(message = "Scholarship name is required")
        String name,

        String provider,

        @JsonProperty("scheme_type")
        @JsonAlias({"schemeType", "scheme_type"})
        String schemeType,

        String eligibility,

        @JsonProperty("maximum_family_income")
        @JsonAlias({"maximumFamilyIncome", "income_limit", "incomeLimit", "maximum_family_income"})
        BigDecimal maximumFamilyIncome,

        @JsonProperty("minimum_marks_or_cgpa")
        @JsonAlias({"minimumMarksOrCgpa", "min_marks", "min_cgpa", "minimum_marks_or_cgpa"})
        Double minimumMarksOrCgpa,

        String course,
        String state,

        @JsonProperty("category_criteria")
        @JsonAlias({"categoryCriteria", "category", "category_criteria"})
        String categoryCriteria,

        @JsonProperty("gender_requirements")
        @JsonAlias({"genderRequirements", "gender", "gender_requirements"})
        String genderRequirements,

        String benefits,
        LocalDate deadline,

        @JsonProperty("required_documents")
        @JsonAlias({"requiredDocuments", "required_documents"})
        String requiredDocuments,

        @JsonProperty("application_url")
        @JsonAlias({"applicationUrl", "application_url"})
        String applicationUrl,

        @JsonProperty("source_url")
        @JsonAlias({"sourceUrl", "source_url"})
        String sourceUrl,

        @JsonProperty("verification_status")
        @JsonAlias({"verificationStatus", "verification_status"})
        String verificationStatus
) {}
