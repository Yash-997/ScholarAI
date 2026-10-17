package com.scholarship.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlineExtractorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    private static List<LocalDate> extract(String text) {
        return List.copyOf(DeadlineExtractor.extract(text, TODAY));
    }

    @Test
    void readsCommonIndianDateFormatsNextToDeadlineWording() {
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("Last date to apply: 26 October 2026."));
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("Deadline - 26/10/2026"));
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("Closing date: 26-10-2026"));
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("Apply by Oct 26, 2026 on the portal"));
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("Deadline: 2026-10-26"));
        assertEquals(List.of(LocalDate.of(2026, 10, 26)), extract("last date is 26th Oct 2026"));
    }

    @Test
    void ignoresDatesThatAreNotNearDeadlineWording() {
        assertTrue(extract("Published on 26 October 2026. Contact us for details.").isEmpty());
    }

    @Test
    void ignoresImpossibleAndImplausibleDates() {
        assertTrue(extract("Deadline 31/02/2026").isEmpty());
        assertTrue(extract("Deadline 01/01/2019").isEmpty());
        assertTrue(extract("Deadline 01/01/2040").isEmpty());
    }

    @Test
    void returnsSeveralCandidatesSortedAndDeduplicated() {
        assertEquals(List.of(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 12, 1)),
                extract("Deadline 01/12/2026 for renewal. Last date for fresh applications is 05-11-2026. Deadline 05-11-2026."));
    }

    @Test
    void blankInputGivesNothing() {
        assertTrue(extract(null).isEmpty());
        assertTrue(extract("   ").isEmpty());
    }
}
