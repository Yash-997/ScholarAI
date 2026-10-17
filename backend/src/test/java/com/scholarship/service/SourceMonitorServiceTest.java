package com.scholarship.service;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import com.scholarship.repository.SourceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
class SourceMonitorServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    @Autowired ScholarshipRepository schemes;
    @Autowired SourceSnapshotRepository snapshots;
    @Autowired DeadlineHistoryRepository history;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;

    /** Programmable fetcher: the next page returned by fetch(). */
    private PageFetcher.Page next;
    private SourceMonitorService monitor;
    private Long schemeId;

    @BeforeEach
    void setUp() {
        SourceRegistry registry = mock(SourceRegistry.class);
        when(registry.load()).thenReturn(List.of(new SourceRegistry.Entry("SRC-T", "Org", "Portal", "https://example.gov.in/scheme",
                LocalDate.of(2026, 10, 1), List.of("1"), "")));
        monitor = newMonitor(registry, url -> next);

        ScholarshipEntity s = TestSchemes.scheme(0, "Tracked Scheme");
        s.setId(null);
        s.setSourceId("SRC-T");
        s.setDeadline(LocalDate.of(2026, 12, 31));
        s.setDeadlineVerifiedOn(LocalDate.of(2026, 10, 1));
        schemeId = schemes.save(s).getId();
    }

    private SourceMonitorService newMonitor(SourceRegistry registry, PageFetcher fetcher) {
        return new SourceMonitorService(snapshots, schemes, history, registry, fetcher, CLOCK,
                new SchemeRefreshService(schemes, history, txManager, CLOCK),
                new SourceDiscoveryService(snapshots, fetcher, 60), mock(ScholarshipIngestionService.class));
    }

    private static PageFetcher.Page page(String text) {
        return new PageFetcher.Page(true, 200, text, null);
    }

    private static final String BASE = "Tracked Scheme scholarship. Eligibility: family income below Rs. 2,50,000. Last date to apply is 31-12-2026.";

    private ScholarshipEntity scheme() {
        return schemes.findById(schemeId).orElseThrow();
    }

    private SourceMonitorService.Outcome check() {
        return monitor.checkAll().get(0).outcome();
    }

    @Test
    void firstSuccessfulFetchStoresABaselineAndFlagsNothing() {
        next = page(BASE);
        assertEquals(SourceMonitorService.Outcome.BASELINE, check());
        assertFalse(scheme().isNeedsReview());
        assertNotNull(scheme().getLastChecked(), "a successful check updates last_checked");
        assertNotNull(snapshots.findById("SRC-T").orElseThrow().getContentHash());
    }

    @Test
    void unchangedContentIsReportedUnchanged() {
        next = page(BASE);
        check();
        assertEquals(SourceMonitorService.Outcome.UNCHANGED, check());
        assertFalse(scheme().isNeedsReview());
    }

    @Test
    void changedContentFlagsTheSchemeForReview_andDoesNotTouchTheDeadline() {
        next = page(BASE);
        check();
        next = page("Tracked Scheme scholarship. Eligibility: family income below Rs. 3,00,000. Last date to apply is 15-01-2027.");
        assertEquals(SourceMonitorService.Outcome.CHANGED, check());

        ScholarshipEntity s = scheme();
        assertTrue(s.isNeedsReview());
        assertTrue(s.getReviewReason().contains("changed"));
        // The live, verified deadline is untouched: a page change is a candidate, not a fact.
        assertEquals(LocalDate.of(2026, 12, 31), s.getDeadline());
        assertEquals(LocalDate.of(2026, 10, 1), s.getDeadlineVerifiedOn());
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(s, TODAY).status());

        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(schemeId);
        assertEquals(1, rows.size());
        assertEquals(DeadlineHistoryEntity.PROPOSED, rows.get(0).getState());
        assertEquals(LocalDate.of(2027, 1, 15), rows.get(0).getDeadline());
    }

    @Test
    void sameProposalIsNotRecordedTwice() {
        next = page(BASE);
        check();
        next = page("Tracked Scheme scholarship. Last date to apply is 15-01-2027. Income below Rs. 3,00,000.");
        check();
        next = page("Tracked Scheme scholarship. Last date to apply is 15-01-2027. Income below Rs. 4,00,000.");
        check();
        long proposed = history.findByScholarshipIdOrderByRecordedAtDesc(schemeId).stream()
                .filter(r -> DeadlineHistoryEntity.PROPOSED.equals(r.getState())).count();
        assertEquals(1, proposed);
    }

    @Test
    void failedFetchNeverChangesDatesOrStatus_andIsNeverTreatedAsClosed() {
        next = page(BASE);
        check();
        ScholarshipEntity before = scheme();
        for (int i = 1; i <= 5; i++) {
            next = PageFetcher.Page.failure(503, "HTTP 503");
            assertEquals(SourceMonitorService.Outcome.FETCH_FAILED, check());
            ScholarshipEntity s = scheme();
            assertEquals(before.getDeadline(), s.getDeadline());
            assertEquals(before.getDeadlineVerifiedOn(), s.getDeadlineVerifiedOn());
            assertEquals("VERIFIED", s.getVerificationStatus());
            assertNotEquals(SchemeStatus.CLOSED, SchemeStatusCalculator.compute(s, TODAY).status());
        }
        assertTrue(history.findByScholarshipIdOrderByRecordedAtDesc(schemeId).isEmpty());
        SourceSnapshotEntity snap = snapshots.findById("SRC-T").orElseThrow();
        assertEquals(5, snap.getConsecutiveFailures());
        assertEquals("HTTP 503", snap.getLastError());
    }

    @Test
    void repeatedFailuresFlagForReviewButOnlyAfterTheThreshold() {
        next = page(BASE);
        check();
        for (int i = 1; i < SourceMonitorService.UNREACHABLE_FLAG_THRESHOLD; i++) {
            next = PageFetcher.Page.failure(null, "SocketTimeoutException: timed out");
            check();
            assertFalse(scheme().isNeedsReview(), "one or two failures are not worth a human's time");
        }
        next = PageFetcher.Page.failure(null, "SocketTimeoutException: timed out");
        check();
        assertTrue(scheme().isNeedsReview());
        assertTrue(scheme().getReviewReason().contains("Nothing was changed"));
        assertEquals(LocalDate.of(2026, 12, 31), scheme().getDeadline());
    }

    @Test
    void aSuccessfulFetchResetsTheFailureCounter() {
        next = PageFetcher.Page.failure(500, "HTTP 500");
        check();
        check();
        next = page(BASE);
        check();
        assertEquals(0, snapshots.findById("SRC-T").orElseThrow().getConsecutiveFailures());
        assertNull(snapshots.findById("SRC-T").orElseThrow().getLastError());
    }

    @Test
    void pageWithNoReadableSchemeContentIsNotTreatedAsAChange() {
        next = page(BASE);
        check();
        String hash = snapshots.findById("SRC-T").orElseThrow().getContentHash();
        next = page("Loading... Please enable JavaScript.");
        assertEquals(SourceMonitorService.Outcome.UNREADABLE, check());
        assertEquals(hash, snapshots.findById("SRC-T").orElseThrow().getContentHash());
        assertFalse(scheme().isNeedsReview());
        assertTrue(snapshots.findById("SRC-T").orElseThrow().getLastError().contains("JavaScript"));
    }

    @Test
    void ambiguousSourceCoveringSeveralSchemesProposesNoDates() {
        ScholarshipEntity other = TestSchemes.scheme(0, "Second Scheme");
        other.setId(null);
        other.setSourceId("SRC-T");
        schemes.save(other);
        next = page(BASE);
        check();
        next = page("Many schemes listed. Last date 20-11-2026 for some. Scholarship income Rs. 5,00,000.");
        check();
        assertTrue(history.findAll().isEmpty(), "with two schemes a date cannot be attributed to either");
        assertTrue(schemes.findBySourceId("SRC-T").stream().allMatch(ScholarshipEntity::isNeedsReview));
    }

    @Test
    void checkAllContinuesWhenTheFetcherThrows() {
        SourceRegistry registry = mock(SourceRegistry.class);
        when(registry.load()).thenReturn(List.of(new SourceRegistry.Entry("SRC-T", "Org", "Portal", "https://example.gov.in/x",
                null, List.of("1"), "")));
        SourceMonitorService bad = newMonitor(registry, url -> {
            throw new IllegalStateException("boom");
        });
        List<SourceMonitorService.CheckResult> results = bad.checkAll();
        assertEquals(1, results.size());
        assertEquals(SourceMonitorService.Outcome.FETCH_FAILED, results.get(0).outcome());
        assertEquals(LocalDate.of(2026, 12, 31), scheme().getDeadline());
    }

    @Test
    void relevantTextIgnoresNavigationAndCounters() {
        String a = SourceMonitorService.relevantText("Home About Contact. Visitors today: 1234. Scholarship income limit is Rs. 2,50,000.");
        String b = SourceMonitorService.relevantText("Home About Contact. Visitors today: 9999. Scholarship income limit is Rs. 2,50,000.");
        assertEquals(a, b);
        assertFalse(new ArrayList<>(List.of(a)).isEmpty());
    }
}
