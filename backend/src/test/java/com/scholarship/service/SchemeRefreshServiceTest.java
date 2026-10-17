package com.scholarship.service;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The rules that decide when a source page may change a record, and what happens when it may not. */
@DataJpaTest
class SchemeRefreshServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final String PAGE = "https://www.buddy4study.com/page/pankh";

    @Autowired ScholarshipRepository schemes;
    @Autowired DeadlineHistoryRepository history;
    @Autowired PlatformTransactionManager tx;
    SchemeRefreshService refresh;
    Long id;

    @BeforeEach
    void setUp() {
        refresh = new SchemeRefreshService(schemes, history, tx, CLOCK);
        ScholarshipEntity s = TestSchemes.scheme(0, "Tata Capital Pankh Scholarship");
        s.setId(null);
        s.setSourceId("SRC-T");
        id = schemes.save(s).getId();
    }

    private SourceSnapshotEntity source(boolean auto, String trust, String itemMatch, boolean discovered) {
        SourceSnapshotEntity src = new SourceSnapshotEntity("SRC-T", PAGE, "Tata Capital", "Buddy4Study");
        src.setKind("BRAND_PAGE");
        src.setAdapter("B4S");
        src.setTrust(trust);
        src.setAutoApply(auto);
        src.setItemMatch(itemMatch);
        src.setDiscovered(discovered);
        return src;
    }

    private SourceSnapshotEntity partner() {
        return source(true, "PARTNER", "General Graduation", false);
    }

    private static SchemeCandidate cand(long extId, String name, String year, String deadline, String launch) {
        return new SchemeCandidate("b4s:" + extId, name, year, launch == null ? null : LocalDate.parse(launch),
                deadline == null ? null : LocalDate.parse(deadline), "Open to students. Annual family income must not exceed INR 4 lakh. Minimum 60% marks.",
                "Up to Rs 18,000", "https://forms.example/apply", "Marksheet", "Buddy4Study programme data, deadline=" + deadline, null, null, PAGE);
    }

    private ScholarshipEntity scheme() {
        return schemes.findById(id).orElseThrow();
    }

    private SchemeRefreshService.Outcome run(SourceSnapshotEntity src, SchemeCandidate... c) {
        return refresh.apply(src, List.of(c), null);
    }

    // ---- refreshing a deadline ------------------------------------------------------------------

    @Test
    void aNewOfficialDeadlineUpdatesTheRecord_withHistoryProvenanceAndReindex() {
        SchemeRefreshService.Outcome o = run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", "2026-06-04"));

        ScholarshipEntity s = scheme();
        assertEquals(LocalDate.of(2026, 10, 26), s.getDeadline());
        assertEquals(LocalDate.of(2026, 6, 4), s.getOpenDate());
        assertEquals("2026-27", s.getAcademicYear());
        assertEquals(TODAY, s.getDeadlineVerifiedOn());
        assertEquals("SOURCE_AUTO", s.getDeadlineOrigin());
        assertEquals(PAGE, s.getDeadlineSourceUrl());
        assertNotNull(s.getDeadlineFetchedAt());
        assertEquals(TODAY, s.getLastVerified());
        assertEquals("b4s:1", s.getExternalKey());
        assertEquals(1, o.updated());
        assertEquals(List.of(id), o.reindex(), "changed text must be re-indexed");

        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(id);
        assertEquals(1, rows.size());
        assertEquals(DeadlineHistoryEntity.APPLIED, rows.get(0).getState());
        assertEquals("SOURCE_AUTO", rows.get(0).getOrigin());
        assertTrue(rows.get(0).getNote().contains("deadline=2026-10-26"));
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(s, TODAY).status());
    }

    @Test
    void aSecondRunWithTheSamePageChangesNothing_idempotent() {
        SchemeCandidate c = cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", "2026-06-04");
        run(partner(), c);
        SchemeRefreshService.Outcome again = run(partner(), c);
        assertEquals(0, again.updated());
        assertTrue(again.reindex().isEmpty());
        assertEquals(1, history.findByScholarshipIdOrderByRecordedAtDesc(id).size());
        assertEquals(1, schemes.count());
    }

    @Test
    void whenTheDeadlineIsExtended_theChangeIsRecordedAndReindexed() {
        run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        SchemeRefreshService.Outcome o = run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-11-20", null));
        assertEquals(LocalDate.of(2026, 11, 20), scheme().getDeadline());
        assertEquals(List.of(id), o.reindex());
        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(id);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> LocalDate.of(2026, 10, 26).equals(r.getPreviousDeadline()) && LocalDate.of(2026, 11, 20).equals(r.getDeadline())));
    }

    @Test
    void aPassedVerifiedDeadlineMakesTheSchemeClosed_notOpen() {
        run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-05", null));
        assertEquals(SchemeStatus.CLOSED, SchemeStatusCalculator.compute(scheme(), TODAY).status());
    }

    @Test
    void theCurrentCycleIsChosen_olderCyclesAreIgnored() {
        run(partner(),
                cand(1, "Tata Capital Pankh Program for General Graduation (2025-26)", "2025-26", "2026-03-13", null),
                cand(2, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        assertEquals(LocalDate.of(2026, 10, 26), scheme().getDeadline());
        assertEquals("2026-27", scheme().getAcademicYear());
        // and an older cycle arriving later never rolls the record back
        run(partner(), cand(1, "Tata Capital Pankh Program for General Graduation (2025-26)", "2025-26", "2026-03-13", null));
        assertEquals(LocalDate.of(2026, 10, 26), scheme().getDeadline());
    }

    @Test
    void programmesOutsideTheItemMatchAreIgnored() {
        SchemeRefreshService.Outcome o = run(partner(), cand(5, "Tata Capital Pankh Scholarship for Class 11th and 12th Students (2026-27)", "2026-27", "2026-10-26", null));
        assertEquals(0, o.updated());
        assertNull(scheme().getDeadline());
    }

    // ---- conflicts, failures and reviewer authority --------------------------------------------

    @Test
    void conflictingDeadlinesForTheSameCycleChangeNothingAndFlagForVerification() {
        ScholarshipEntity s = scheme();
        s.setDeadline(LocalDate.of(2026, 12, 31));
        s.setDeadlineVerifiedOn(LocalDate.of(2026, 10, 1));
        s.setDeadlineOrigin("DATASET");
        schemes.save(s);
        SchemeRefreshService.Outcome o = run(partner(),
                cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null),
                cand(2, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-11-30", null));
        ScholarshipEntity after = scheme();
        assertEquals(LocalDate.of(2026, 12, 31), after.getDeadline(), "last valid data preserved");
        assertTrue(after.isNeedsReview());
        assertTrue(after.getReviewReason().contains("conflicting"));
        assertEquals(1, o.flagged());
        assertTrue(history.findByScholarshipIdOrderByRecordedAtDesc(id).isEmpty());
    }

    @Test
    void aReviewerVerifiedDateIsNeverOverwrittenAutomatically() {
        ScholarshipEntity s = scheme();
        s.setDeadline(LocalDate.of(2026, 11, 30));
        s.setDeadlineVerifiedOn(LocalDate.of(2026, 10, 10));
        s.setDeadlineOrigin("ADMIN");
        schemes.save(s);
        run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        ScholarshipEntity after = scheme();
        assertEquals(LocalDate.of(2026, 11, 30), after.getDeadline());
        assertEquals("ADMIN", after.getDeadlineOrigin());
        assertTrue(after.isNeedsReview());
        List<DeadlineHistoryEntity> rows = history.findByScholarshipIdOrderByRecordedAtDesc(id);
        assertEquals(1, rows.size());
        assertEquals(DeadlineHistoryEntity.PROPOSED, rows.get(0).getState());
        assertEquals(LocalDate.of(2026, 10, 26), rows.get(0).getDeadline());
    }

    @Test
    void sourcesThatMayNotAutoApplyOnlyPropose() {
        run(source(false, "OFFICIAL", "General Graduation", false), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        assertNull(scheme().getDeadline());
        assertTrue(scheme().isNeedsReview());
        assertEquals(DeadlineHistoryEntity.PROPOSED, history.findByScholarshipIdOrderByRecordedAtDesc(id).get(0).getState());
    }

    @Test
    void aggregatorsNeverUpdateRecordsEvenWhenFlaggedAutoApply() {
        run(source(true, "AGGREGATOR", "General Graduation", false), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        assertNull(scheme().getDeadline());
        assertTrue(scheme().isNeedsReview());
    }

    @Test
    void aSourceMappedToSeveralRecordsIsAmbiguousAndChangesNothing() {
        ScholarshipEntity other = TestSchemes.scheme(0, "Another Tata Scheme");
        other.setId(null);
        other.setSourceId("SRC-T");
        schemes.save(other);
        SchemeRefreshService.Outcome o = run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        assertEquals(2, o.flagged());
        schemes.findAll().forEach(s -> assertNull(s.getDeadline()));
    }

    @Test
    void anAmbiguousPageNeverSetsADeadline() {
        SourceSnapshotEntity src = source(true, "OFFICIAL", null, false);
        src.setKind("SCHEME_PAGE");
        src.setAdapter("HTML");
        SchemeCandidate amb = new SchemeCandidate(null, "Tata Capital Pankh Scholarship", null, null, null, null, null, null, null,
                "AMBIGUOUS: several dates near deadline wording: [2026-10-26, 2026-11-30]", null, null, PAGE);
        SchemeRefreshService.Outcome o = run(src, amb);
        assertNull(scheme().getDeadline());
        assertEquals(1, o.flagged());
    }

    // ---- explicit window notices ---------------------------------------------------------------

    private SourceSnapshotEntity officialPage() {
        SourceSnapshotEntity src = source(true, "OFFICIAL", null, false);
        src.setKind("SCHEME_PAGE");
        src.setAdapter("HTML");
        return src;
    }

    private SchemeCandidate notice(String state, String evidence) {
        return new SchemeCandidate(null, "Tata Capital Pankh Scholarship", null, null, null, null, null, null, null, null, state, evidence, PAGE);
    }

    @Test
    void anOfficialClosedNoticeMakesTheSchemeClosedWithEvidence() {
        run(officialPage(), notice(SchemeStatusCalculator.CLOSED_NOTICE, "Page text: \"Applications ... are now closed\""));
        ScholarshipEntity s = scheme();
        assertEquals(SchemeStatusCalculator.CLOSED_NOTICE, s.getWindowState());
        assertEquals(SchemeStatus.CLOSED, SchemeStatusCalculator.compute(s, TODAY).status());
        assertTrue(s.getWindowEvidence().contains("now closed"));
        assertNull(s.getDeadline(), "no date is invented");
    }

    @Test
    void aRollingNoticeSupportedByEvidenceMakesTheSchemeOpen() {
        run(officialPage(), notice(SchemeStatusCalculator.ROLLING_OPEN, "Page text: \"accepted on a rolling basis\""));
        assertEquals(SchemeStatus.OPEN, SchemeStatusCalculator.compute(scheme(), TODAY).status());
    }

    @Test
    void aClosedNoticeThatContradictsAFutureVerifiedDeadlineChangesNothing() {
        ScholarshipEntity s = scheme();
        s.setDeadline(TODAY.plusDays(20));
        s.setDeadlineVerifiedOn(TODAY.minusDays(2));
        s.setDeadlineOrigin("DATASET");
        schemes.save(s);
        SchemeRefreshService.Outcome o = run(officialPage(), notice(SchemeStatusCalculator.CLOSED_NOTICE, "x"));
        assertNull(scheme().getWindowState());
        assertTrue(scheme().isNeedsReview());
        assertEquals(1, o.flagged());
    }

    @Test
    void aPageThatNoLongerStatesTheWindowKeepsTheLastValidStatusAndFlags() {
        run(officialPage(), notice(SchemeStatusCalculator.CLOSED_NOTICE, "x"));
        SchemeCandidate silent = new SchemeCandidate(null, "Tata Capital Pankh Scholarship", null, null, null, null, null, null, null, "text", null, null, PAGE);
        run(officialPage(), silent);
        assertEquals(SchemeStatusCalculator.CLOSED_NOTICE, scheme().getWindowState());
        assertTrue(scheme().isNeedsReview());
    }

    @Test
    void aNewExplicitDeadlineSupersedesAnEarlierWindowNotice() {
        run(officialPage(), notice(SchemeStatusCalculator.CLOSED_NOTICE, "x"));
        run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2027-28)", "2027-28", "2027-10-26", "2027-06-01"));
        assertNull(scheme().getWindowState());
        assertEquals(LocalDate.of(2027, 10, 26), scheme().getDeadline());
    }

    @Test
    void aCleanRefreshClearsAnEarlierSourceFlag() {
        ScholarshipEntity s = scheme();
        s.setNeedsReview(true);
        s.setReviewReason("Source SRC-T lists conflicting deadlines for the same programme and cycle: [a, b].");
        schemes.save(s);
        run(partner(), cand(1, "Tata Capital Pankh Programme for General Graduation (2026-27)", "2026-27", "2026-10-26", null));
        assertFalse(scheme().isNeedsReview());
    }

    // ---- discovery of programmes that are not in the dataset -----------------------------------

    private SourceSnapshotEntity discoveredBrand() {
        SourceSnapshotEntity src = new SourceSnapshotEntity("D-abc", "https://www.buddy4study.com/page/jk-tyre", "JK Tyre", "Buddy4Study (discovered)");
        src.setKind("BRAND_PAGE");
        src.setAdapter("B4S");
        src.setTrust("PARTNER");
        src.setAutoApply(true);
        src.setDiscovered(true);
        return src;
    }

    private SchemeCandidate jk(long extId, String year, String deadline) {
        return new SchemeCandidate("b4s:" + extId, "JK Tyre Shiksha Sarthi Scholarship " + year, year, null, LocalDate.parse(deadline),
                "Students must score at least 75% marks. Family income should not exceed INR 3 lakh.", "Rs 12,000", "https://apply.example/jk", "Marksheet", "ev", null, null, "https://www.buddy4study.com/page/jk-tyre");
    }

    @Test
    void anUnknownProgrammeIsStagedForReview_notPublished_andParsedCriteriaAreNotApplied() {
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-11-10")), null);
        ScholarshipEntity d = schemes.findByExternalKey("b4s:77").get(0);
        assertEquals("PENDING_REVIEW", d.getVerificationStatus());
        assertTrue(d.isNeedsReview());
        assertEquals(LocalDate.of(2026, 11, 10), d.getDeadline());
        assertEquals(0, new java.math.BigDecimal("300000").compareTo(d.getMaximumFamilyIncome()));
        assertEquals(75.0, d.getMinimumMarksOrCgpa());
        assertEquals(SchemeStatus.UNVERIFIED, SchemeStatusCalculator.compute(d, TODAY).status(), "staged records never show as open");
    }

    @Test
    void discoveryIsIdempotent_noDuplicates() {
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-11-10")), null);
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-11-10")), null);
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-11-10")), null);
        assertEquals(1, schemes.findByExternalKey("b4s:77").size());
        assertEquals(2, schemes.count(), "the seeded record plus one staged programme");
    }

    @Test
    void aNewCycleOfAStagedProgrammeUpdatesItInsteadOfCreatingAnother() {
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-11-10")), null);
        refresh.apply(discoveredBrand(), List.of(jk(77, "2026-27", "2026-12-01")), null);   // deadline extended
        assertEquals(2, schemes.count());
        assertEquals(LocalDate.of(2026, 12, 1), schemes.findByExternalKey("b4s:77").get(0).getDeadline());
    }

    @Test
    void oldCyclesAreNotStaged() {
        refresh.apply(discoveredBrand(), List.of(jk(10, "2024-25", "2025-01-31")), null);
        assertEquals(1, schemes.count());
    }

    @Test
    void aProgrammeAlreadyInTheDatasetIsNeverStagedAsADuplicate() {
        SchemeCandidate same = new SchemeCandidate("b4s:5", "Tata Capital Pankh Scholarship", "2026-27", null, LocalDate.of(2026, 11, 1),
                null, null, null, null, "ev", null, null, "https://www.buddy4study.com/page/jk-tyre");
        refresh.apply(discoveredBrand(), List.of(same), null);
        assertEquals(1, schemes.count());
    }

    @Test
    void parsersReadIncomeAndMarksOnlyWhenTheyAreStated() {
        assertEquals(0, new java.math.BigDecimal("600000").compareTo(SchemeRefreshService.parseIncome("income must not exceed INR 6 lakh")));
        assertEquals(0, new java.math.BigDecimal("250000").compareTo(SchemeRefreshService.parseIncome("family income below Rs. 2,50,000 per annum")));
        assertNull(SchemeRefreshService.parseIncome("income is considered"));
        assertEquals(60.0, SchemeRefreshService.parseMarks("a minimum of 60% marks"));
        assertNull(SchemeRefreshService.parseMarks("good marks are required"));
        assertEquals(SchemeRefreshService.stem("Pankh Program (2025-26)"), SchemeRefreshService.stem("Pankh Programme 2026-27"));
    }
}
