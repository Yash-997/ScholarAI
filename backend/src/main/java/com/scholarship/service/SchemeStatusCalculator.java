package com.scholarship.service;

import com.scholarship.entity.ScholarshipEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pure function from (record, today) to a status. No I/O, no clock access: callers pass {@code today}.
 *
 * <ul>
 *   <li>Record not VERIFIED -> UNVERIFIED.</li>
 *   <li>Nothing verified ({@code verifiedOn} null) or no dates and no window notice -> UNKNOWN. A reported but unverified
 *       deadline is never used to say a scheme is open or closed.</li>
 *   <li>Verified opening date after today -> UPCOMING.</li>
 *   <li>Verified deadline before today -> CLOSED; on the deadline day it is still OPEN.</li>
 *   <li>With no dates, an explicit official-page notice decides: ROLLING_OPEN -> OPEN, CLOSED_NOTICE -> CLOSED.</li>
 *   <li>If a closed notice contradicts a future verified deadline, the answer is UNKNOWN (flagged for a human).</li>
 * </ul>
 */
public final class SchemeStatusCalculator {

    public static final String ROLLING_OPEN = "ROLLING_OPEN";
    public static final String CLOSED_NOTICE = "CLOSED_NOTICE";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SchemeStatusCalculator() {
    }

    public record Result(SchemeStatus status, String note) {
    }

    public static Result compute(ScholarshipEntity s, LocalDate today) {
        return compute(s.getVerificationStatus(), s.getOpenDate(), s.getDeadline(), s.getDeadlineVerifiedOn(), s.getWindowState(), today);
    }

    public static Result compute(String verificationStatus, LocalDate openDate, LocalDate deadline,
                                 LocalDate deadlineVerifiedOn, LocalDate today) {
        return compute(verificationStatus, openDate, deadline, deadlineVerifiedOn, null, today);
    }

    public static Result compute(String verificationStatus, LocalDate openDate, LocalDate deadline,
                                 LocalDate deadlineVerifiedOn, String windowState, LocalDate today) {
        if (!"VERIFIED".equals(verificationStatus)) {
            return new Result(SchemeStatus.UNVERIFIED, "This record has not been verified.");
        }
        boolean hasDates = openDate != null || deadline != null;
        if (deadlineVerifiedOn == null) {
            if (!hasDates) {
                return new Result(SchemeStatus.UNKNOWN, "No application dates are on record. Check the official portal.");
            }
            String reported = deadline != null ? " A deadline of " + FMT.format(deadline) + " was reported but is not verified." : "";
            return new Result(SchemeStatus.UNKNOWN, "Application dates are not verified." + reported + " Check the official portal.");
        }
        if (!hasDates) {
            if (ROLLING_OPEN.equals(windowState)) {
                return new Result(SchemeStatus.OPEN, "The official page states applications are open on a rolling basis (checked " + FMT.format(deadlineVerifiedOn) + ").");
            }
            if (CLOSED_NOTICE.equals(windowState)) {
                return new Result(SchemeStatus.CLOSED, "The official page states applications are closed (checked " + FMT.format(deadlineVerifiedOn) + "). Dates for the next cycle are not known.");
            }
            return new Result(SchemeStatus.UNKNOWN, "No application dates are on record. Check the official portal.");
        }
        if (openDate != null && today.isBefore(openDate)) {
            return new Result(SchemeStatus.UPCOMING, "Applications open on " + FMT.format(openDate) + ".");
        }
        if (deadline == null) {
            return new Result(SchemeStatus.UNKNOWN, "The closing date is not on record. Check the official portal.");
        }
        if (today.isAfter(deadline)) {
            return new Result(SchemeStatus.CLOSED,
                    "The verified deadline of " + FMT.format(deadline) + " has passed. Dates for the next cycle are not known.");
        }
        if (CLOSED_NOTICE.equals(windowState)) {
            return new Result(SchemeStatus.UNKNOWN, "Sources conflict: a deadline of " + FMT.format(deadline)
                    + " is on record but the official page says applications are closed. Check the official portal.");
        }
        return new Result(SchemeStatus.OPEN, "Verified deadline: " + FMT.format(deadline) + ".");
    }
}
