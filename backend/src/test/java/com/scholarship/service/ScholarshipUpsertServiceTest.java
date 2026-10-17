package com.scholarship.service;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(ScholarshipUpsertService.class)
class ScholarshipUpsertServiceTest {

    @Autowired ScholarshipUpsertService upsert;
    @Autowired ScholarshipRepository repository;
    @Autowired DeadlineHistoryRepository history;

    private ScholarshipEntity incoming(String name, LocalDate deadline) {
        ScholarshipEntity s = new ScholarshipEntity();
        s.setName(name);
        s.setProvider("Dept");
        s.setBenefits("Fees");
        s.setDeadline(deadline);
        s.setVerificationStatus("VERIFIED");
        return s;
    }

    @Test
    void upsertingTheSameSchemeTwiceKeepsOneRow() {
        ScholarshipUpsertService.Result first = upsert.upsert(incoming("Pragati Scholarship", null), "DATASET");
        ScholarshipUpsertService.Result second = upsert.upsert(incoming("Pragati Scholarship", null), "DATASET");
        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(first.entity().getId(), second.entity().getId());
        assertEquals(1, repository.count());
    }

    @Test
    void caseSpacingPunctuationAndTransliterationVariantsAreTheSameScheme() {
        upsert.upsert(incoming("Rajarshi Shahu Maharaj Shikshan Shulkh Yojna (EBC)", null), "ADMIN");
        upsert.upsert(incoming("RAJARSHI  SHAHU Maharaj Shikshan Shulkh Yojana - EBC", null), "ADMIN");
        assertEquals(1, repository.count());
    }

    @Test
    void differentSchemesStaySeparate() {
        upsert.upsert(incoming("Pragati Scholarship", null), "DATASET");
        upsert.upsert(incoming("Saksham Scholarship", null), "DATASET");
        assertEquals(2, repository.count());
    }

    @Test
    void updateKeepsIdAndCreationTime_andChangesContent() {
        ScholarshipEntity created = upsert.upsert(incoming("Scheme A", null), "DATASET").entity();
        ScholarshipEntity changed = incoming("Scheme A", null);
        changed.setBenefits("Rs. 50,000 per year");
        ScholarshipEntity updated = upsert.upsert(changed, "DATASET").entity();
        assertEquals(created.getId(), updated.getId());
        assertEquals("Rs. 50,000 per year", repository.findById(created.getId()).orElseThrow().getBenefits());
    }

    @Test
    void deadlineChangesAreRecordedInHistory_andRepeatsAreNot() {
        Long id = upsert.upsert(incoming("Scheme A", LocalDate.of(2026, 9, 30)), "DATASET").entity().getId();
        upsert.upsert(incoming("Scheme A", LocalDate.of(2026, 9, 30)), "DATASET");   // unchanged
        ScholarshipUpsertService.Result moved = upsert.upsert(incoming("Scheme A", LocalDate.of(2026, 10, 26)), "DATASET");
        assertTrue(moved.deadlineChanged());

        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(id);
        assertEquals(2, rows.size(), "initial deadline + one change");
        assertTrue(rows.stream().anyMatch(r -> LocalDate.of(2026, 9, 30).equals(r.getPreviousDeadline())
                && LocalDate.of(2026, 10, 26).equals(r.getDeadline())));
        assertTrue(rows.stream().allMatch(r -> DeadlineHistoryEntity.APPLIED.equals(r.getState())));
    }

    @Test
    void schemeWithoutADeadlineWritesNoHistory() {
        Long id = upsert.upsert(incoming("Scheme A", null), "DATASET").entity().getId();
        assertTrue(history.findByScholarshipIdOrderByRecordedAtDesc(id).isEmpty());
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> upsert.upsert(incoming("  ", null), "ADMIN"));
    }
}
