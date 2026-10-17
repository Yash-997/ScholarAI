package com.scholarship.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * One recommended scheme. Facts come from PostgreSQL; only {@code eligibilityReason} may be LLM-written.
 * {@code status}/{@code statusNote} are computed from verified dates; {@code matchedCriteria} is the deterministic
 * list of criteria that were checked and passed.
 */
public record ScholarshipRecommendationDto(
        @JsonProperty("scholarship_id") Long scholarshipId,
        @JsonProperty("scholarship_name") String scholarshipName,
        String provider,
        String benefits,
        @JsonProperty("eligibility_reason") String eligibilityReason,
        @JsonProperty("required_documents") List<String> requiredDocuments,
        LocalDate deadline,
        @JsonProperty("application_url") String applicationUrl,
        String status,
        @JsonProperty("status_note") String statusNote,
        @JsonProperty("deadline_verified_on") LocalDate deadlineVerifiedOn,
        @JsonProperty("source_url") String sourceUrl,
        @JsonProperty("last_verified") LocalDate lastVerified,
        @JsonProperty("needs_review") boolean needsReview,
        @JsonProperty("matched_criteria") List<String> matchedCriteria,
        @JsonProperty("academic_year") String academicYear,
        @JsonProperty("open_date") LocalDate openDate,
        @JsonProperty("window_state") String windowState,
        @JsonProperty("window_evidence") String windowEvidence,
        /** DATASET, ADMIN (a reviewer verified it) or SOURCE_AUTO (read from the official/partner page). */
        @JsonProperty("deadline_origin") String deadlineOrigin,
        @JsonProperty("deadline_source_url") String deadlineSourceUrl,
        @JsonProperty("deadline_fetched_at") java.time.OffsetDateTime deadlineFetchedAt
) {}
