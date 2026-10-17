package com.scholarship.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Student criteria for the recommendation pipeline. All fields are optional; a null field is not filtered on.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendRequestDto(
        String query,

        @JsonAlias({"familyIncome", "income"})
        BigDecimal annualFamilyIncome,

        @JsonAlias({"currentCgpaOrPercentage", "marksOrCgpa", "marks"})
        Double marks,

        @JsonAlias({"stateOfDomicile"})
        String state,

        @JsonAlias({"socialCategory"})
        String category,

        String course,
        String gender,

        /** Closed schemes are hidden unless this is true (they are returned last, flagged CLOSED). */
        @JsonAlias({"include_closed"})
        Boolean includeClosed
) {
    public RecommendRequestDto(String query, BigDecimal annualFamilyIncome, Double marks, String state, String category,
                               String course, String gender) {
        this(query, annualFamilyIncome, marks, state, category, course, gender, null);
    }

    public boolean wantsClosed() {
        return Boolean.TRUE.equals(includeClosed);
    }
}
