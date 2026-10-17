package com.scholarship.service;

import com.scholarship.dto.ScholarshipDto;
import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.exception.ResourceNotFoundException;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ScholarshipReviewServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @Autowired ScholarshipRepository schemes;
    @Autowired DeadlineHistoryRepository history;
    ScholarshipReviewService review;
    Long id;

    @BeforeEach
    void setUp() {
        review = new ScholarshipReviewService(schemes, history, CLOCK);
        ScholarshipEntity s = TestSchemes.scheme(0, "Reviewed Scheme");
        s.setId(null);
        s.setDeadline(LocalDate.of(2026, 9, 1));
        s.setNeedsReview(true);
        s.setReviewReason("source changed");
        id = schemes.save(s).getId();
    }

    @Test
    void anUnverifiedDeadlineGivesUnknown_untilAReviewerVerifiesIt() {
        assertEquals("UNKNOWN", ScholarshipMapper.toDto(schemes.findById(id).orElseThrow(), LocalDate.of(2026, 10, 17)).status());

        ScholarshipDto dto = review.applyVerifiedDates(id, null, LocalDate.of(2026, 11, 30), "2026-27", "checked on portal");
        assertEquals("OPEN", dto.status());
        assertEquals(LocalDate.of(2026, 10, 17), dto.deadlineVerifiedOn());
        assertFalse(dto.needsReview());
        assertNull(dto.reviewReason());
        assertEquals("2026-27", dto.academicYear());
    }

    @Test
    void verifiedPastDeadlineIsClosed() {
        ScholarshipDto dto = review.applyVerifiedDates(id, null, LocalDate.of(2026, 10, 5), null, null);
        assertEquals("CLOSED", dto.status());
    }

    @Test
    void verifiedFutureOpenDateIsUpcoming() {
        ScholarshipDto dto = review.applyVerifiedDates(id, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 31), null, null);
        assertEquals("UPCOMING", dto.status());
    }

    @Test
    void changingTheDeadlineWritesHistory_confirmingTheSameOneDoesNot() {
        review.applyVerifiedDates(id, null, LocalDate.of(2026, 11, 30), null, "first");
        review.applyVerifiedDates(id, null, LocalDate.of(2026, 11, 30), null, "second look, same date");
        List<DeadlineHistoryEntity> rows = review.deadlineHistory(id);
        assertEquals(1, rows.size());
        assertEquals(LocalDate.of(2026, 9, 1), rows.get(0).getPreviousDeadline());
        assertEquals(LocalDate.of(2026, 11, 30), rows.get(0).getDeadline());
        assertEquals("ADMIN", rows.get(0).getOrigin());
    }

    @Test
    void rejectsEmptyAndInvertedInput() {
        assertThrows(IllegalArgumentException.class, () -> review.applyVerifiedDates(id, null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> review.applyVerifiedDates(id, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1), null, null));
        assertThrows(ResourceNotFoundException.class, () -> review.applyVerifiedDates(9999L, null, LocalDate.of(2026, 11, 1), null, null));
    }

    @Test
    void flaggedListsOnlySchemesNeedingReview_andClearWorks() {
        assertEquals(1, review.flagged().size());
        review.clearReview(id);
        assertTrue(review.flagged().isEmpty());
    }
}
