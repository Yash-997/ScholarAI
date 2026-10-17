package com.scholarship.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemeStatusCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final LocalDate VERIFIED = LocalDate.of(2026, 10, 1);

    private SchemeStatusCalculator.Result compute(String record, LocalDate open, LocalDate deadline, LocalDate verifiedOn) {
        return SchemeStatusCalculator.compute(record, open, deadline, verifiedOn, TODAY);
    }

    @Test
    void verifiedFutureDeadlineIsOpen() {
        assertEquals(SchemeStatus.OPEN, compute("VERIFIED", null, TODAY.plusDays(9), VERIFIED).status());
    }

    @Test
    void deadlineDayItselfIsStillOpen_dayAfterIsClosed() {
        assertEquals(SchemeStatus.OPEN, compute("VERIFIED", null, TODAY, VERIFIED).status());
        assertEquals(SchemeStatus.CLOSED, compute("VERIFIED", null, TODAY.minusDays(1), VERIFIED).status());
    }

    @Test
    void verifiedOpenDateInFutureIsUpcoming() {
        SchemeStatusCalculator.Result r = compute("VERIFIED", TODAY.plusDays(20), TODAY.plusDays(60), VERIFIED);
        assertEquals(SchemeStatus.UPCOMING, r.status());
        assertTrue(r.note().contains("open"));
    }

    @Test
    void openedAlreadyWithFutureDeadlineIsOpen() {
        assertEquals(SchemeStatus.OPEN, compute("VERIFIED", TODAY.minusDays(5), TODAY.plusDays(5), VERIFIED).status());
    }

    @Test
    void noDatesAtAllIsUnknown() {
        assertEquals(SchemeStatus.UNKNOWN, compute("VERIFIED", null, null, null).status());
        assertEquals(SchemeStatus.UNKNOWN, compute("VERIFIED", null, null, VERIFIED).status());
    }

    @Test
    void passedButUnverifiedDeadlineIsNeverClosed() {
        // A reported date that nobody verified must not be used to declare a scheme closed (or open).
        SchemeStatusCalculator.Result past = compute("VERIFIED", null, TODAY.minusDays(30), null);
        assertEquals(SchemeStatus.UNKNOWN, past.status());
        assertTrue(past.note().contains("not verified"));
        assertEquals(SchemeStatus.UNKNOWN, compute("VERIFIED", null, TODAY.plusDays(30), null).status());
    }

    @Test
    void openDateKnownButNoDeadlineIsUnknown() {
        assertEquals(SchemeStatus.UNKNOWN, compute("VERIFIED", TODAY.minusDays(3), null, VERIFIED).status());
    }

    @Test
    void recordThatIsNotVerifiedIsUnverifiedWhateverItsDates() {
        assertEquals(SchemeStatus.UNVERIFIED, compute("PENDING_REVIEW", null, TODAY.plusDays(5), VERIFIED).status());
        assertEquals(SchemeStatus.UNVERIFIED, compute("ARCHIVED_LEGACY", null, TODAY.minusDays(5), VERIFIED).status());
        assertEquals(SchemeStatus.UNVERIFIED, compute(null, null, null, null).status());
    }
}
