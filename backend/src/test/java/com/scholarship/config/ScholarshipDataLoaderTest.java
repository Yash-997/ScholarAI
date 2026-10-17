package com.scholarship.config;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import com.scholarship.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DataJpaTest
@Import(ScholarshipUpsertService.class)
class ScholarshipDataLoaderTest {

    private static final String HEADER = "id,name,provider,course,year,min_marks,max_income,state,category,gender,benefits,deadline,docs,urls,open_date,deadline_verified,academic_year\n";
    private static final String REGISTRY_HEADER = "source_id,organization,portal_name,source_url,document_title,access_date,schemes_covered,verification_notes\n";

    @Autowired ScholarshipRepository repository;
    @Autowired ScholarshipUpsertService upsert;
    @Autowired DeadlineHistoryRepository history;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @TempDir Path dir;

    private ScholarshipDataLoader loader() {
        SourceRegistry registry = new SourceRegistry(dir.resolve("scholarships.csv").toString());
        SourceMonitorService monitor = mock(SourceMonitorService.class);
        return new ScholarshipDataLoader(repository, upsert, mock(ScholarshipIngestionService.class), monitor, registry,
                mock(JdbcTemplate.class), dir.resolve("scholarships.csv").toString(), true);
    }

    private void writeDataset(String... rows) throws Exception {
        Files.writeString(dir.resolve("scholarships.csv"), HEADER + String.join("\n", rows) + "\n");
        Files.writeString(dir.resolve("source_registry.csv"), REGISTRY_HEADER
                + "SRC-01,Org,Portal,https://example.gov.in/,Doc,2026-10-01,1;2,notes\n");
    }

    private static String row(int id, String name, String deadline, String verified) {
        return id + "," + name + ",Dept,Engineering,1-4,60,250000,ALL,ALL,ALL,Fees," + deadline + ",Income Certificate,https://example.gov.in/apply|https://example.gov.in/src,,"
                + verified + "," + (deadline.isEmpty() ? "" : "2026-27");
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void loadingTwiceCreatesNoDuplicates() throws Exception {
        writeDataset(row(1, "Alpha Scheme", "", ""), row(2, "Beta Scheme", "2026-11-30", "2026-10-01"));
        loader().loadCsv();
        loader().loadCsv();
        loader().loadCsv();
        assertEquals(2, repository.count());
    }

    @Test
    void lastVerifiedComesFromTheRegistry_notFromTheStartupClock() throws Exception {
        writeDataset(row(1, "Alpha Scheme", "", ""), row(3, "Gamma Scheme", "", ""));
        loader().loadCsv();
        ScholarshipEntity covered = repository.findByDedupeKey("alphascheme").get(0);
        ScholarshipEntity uncovered = repository.findByDedupeKey("gammascheme").get(0);
        assertEquals(LocalDate.of(2026, 10, 1), covered.getLastVerified());
        assertEquals("SRC-01", covered.getSourceId());
        assertNull(uncovered.getLastVerified(), "no registry source, so no verification date is claimed");
    }

    @Test
    void aDeadlineWithoutAVerificationDateStaysUnverified() throws Exception {
        writeDataset(row(1, "Alpha Scheme", "2026-09-30", ""), row(2, "Beta Scheme", "2026-11-30", "2026-10-01"));
        loader().loadCsv();
        ScholarshipEntity alpha = repository.findByDedupeKey("alphascheme").get(0);
        ScholarshipEntity beta = repository.findByDedupeKey("betascheme").get(0);
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertEquals(SchemeStatus.UNKNOWN, SchemeStatusCalculator.compute(alpha, today).status());
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(beta, today).status());
    }

    @Test
    void unchangedRowsAreSkipped_soAReviewersVerifiedDatesSurviveARestart() throws Exception {
        writeDataset(row(1, "Alpha Scheme", "", ""));
        loader().loadCsv();
        ScholarshipEntity alpha = repository.findByDedupeKey("alphascheme").get(0);
        alpha.setDeadline(LocalDate.of(2027, 1, 31));
        alpha.setDeadlineVerifiedOn(LocalDate.of(2026, 10, 16));
        repository.save(alpha);

        loader().loadCsv();   // CSV unchanged: must not clobber the reviewer's work

        ScholarshipEntity after = repository.findByDedupeKey("alphascheme").get(0);
        assertEquals(LocalDate.of(2027, 1, 31), after.getDeadline());
        assertEquals(LocalDate.of(2026, 10, 16), after.getDeadlineVerifiedOn());
    }

    @Test
    void editingTheCsvUpdatesTheRowInPlace_andRecordsTheDeadlineChange() throws Exception {
        writeDataset(row(1, "Alpha Scheme", "2026-10-05", "2026-10-01"));
        loader().loadCsv();
        Long id = repository.findByDedupeKey("alphascheme").get(0).getId();

        writeDataset(row(1, "Alpha Scheme", "2026-12-05", "2026-10-02"));
        loader().loadCsv();

        assertEquals(1, repository.count());
        ScholarshipEntity after = repository.findById(id).orElseThrow();
        assertEquals(LocalDate.of(2026, 12, 5), after.getDeadline());
        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(id);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> LocalDate.of(2026, 10, 5).equals(r.getPreviousDeadline()) && LocalDate.of(2026, 12, 5).equals(r.getDeadline())));
    }

    @Test
    void rowsMissingFromTheCsvAreArchivedNotDeleted() throws Exception {
        repository.save(TestSchemesLite.legacy("Old Seeded Scheme"));
        ScholarshipEntity staged = TestSchemesLite.legacy("Staged Scheme");
        staged.setVerificationStatus("PENDING_REVIEW");
        repository.save(staged);

        writeDataset(row(1, "Alpha Scheme", "", ""));
        loader().run(null);

        assertEquals(3, repository.count(), "nothing is deleted");
        assertEquals("ARCHIVED_LEGACY", repository.findByDedupeKey("oldseededscheme").get(0).getVerificationStatus());
        assertEquals("PENDING_REVIEW", repository.findByDedupeKey("stagedscheme").get(0).getVerificationStatus(), "staging rows are left alone");
        assertEquals("VERIFIED", repository.findByDedupeKey("alphascheme").get(0).getVerificationStatus());
        assertEquals(SchemeStatus.UNVERIFIED,
                SchemeStatusCalculator.compute(repository.findByDedupeKey("oldseededscheme").get(0), LocalDate.of(2026, 10, 17)).status());
    }

    @Test
    void rowsWrittenBeforeTheDedupeColumnExistedAreMatchedNotDuplicated() throws Exception {
        repository.saveAndFlush(TestSchemesLite.legacy("Alpha Scheme"));
        jdbc.update("update scholarships set dedupe_key = null");   // the state of a database from before this column existed
        em.clear();
        assertTrue(repository.findByDedupeKey("alphascheme").isEmpty());

        writeDataset(row(1, "Alpha Scheme", "", ""));
        loader().run(null);

        assertEquals(1, repository.count());
        assertEquals(1, repository.findByDedupeKey("alphascheme").size());
    }

    /** Minimal VERIFIED row, not tied to the service-package test helper. */
    private static final class TestSchemesLite {
        static ScholarshipEntity legacy(String name) {
            ScholarshipEntity s = new ScholarshipEntity();
            s.setName(name);
            s.setVerificationStatus("VERIFIED");
            return s;
        }
    }
}
