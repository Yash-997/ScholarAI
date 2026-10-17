package com.scholarship.service;

import java.time.LocalDate;

/**
 * What a source page says about one scheme. Everything here was read from the page; nothing is inferred or defaulted.
 * Absent facts stay null.
 *
 * @param externalKey  stable id from a structured source (for example "b4s:26637"), null for plain HTML pages
 * @param evidence     the wording or field the dates were read from, kept as an audit trail
 * @param windowState  ROLLING_OPEN or CLOSED_NOTICE when the page says so explicitly, else null
 */
public record SchemeCandidate(String externalKey, String name, String academicYear, LocalDate openDate, LocalDate deadline,
                              String eligibilityText, String benefits, String applicationUrl, String documents,
                              String evidence, String windowState, String windowEvidence, String pageUrl) {

    /** Start year of the academic year ("2026-27" -> 2026), or 0 when unknown. Used to pick the current cycle. */
    public int academicYearStart() {
        if (academicYear == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(20\\d\\d)").matcher(academicYear);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
