package com.scholarship.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExtractorsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);

    // ---- Buddy4Study structured data ------------------------------------------------------------

    @Test
    void readsEveryProgrammeCycleWithItsOwnDates() {
        String html = TestPages.brandPage(
                TestPages.item(1, "Pankh Scholarship for Graduation (2026-27)", "2026-10-26", "2026-06-04", "2026", "Open to <b>graduates</b> with 60% marks."),
                TestPages.item(2, "Pankh Scholarship for Graduation (2025\\u201326)", "2026-03-13", "2025-11-10", "2025", "Old cycle."));
        List<SchemeCandidate> c = BuddyBrandExtractor.extract(html, "https://www.buddy4study.com/page/pankh");
        assertEquals(2, c.size());
        SchemeCandidate cur = c.get(0);
        assertEquals("b4s:1", cur.externalKey());
        assertEquals(LocalDate.of(2026, 10, 26), cur.deadline());
        assertEquals(LocalDate.of(2026, 6, 4), cur.openDate());
        assertEquals("2026-27", cur.academicYear());
        assertEquals("Open to graduates with 60% marks.", cur.eligibilityText());
        assertEquals("Up to INR 50,000", cur.benefits());
        assertEquals("Marksheet ID proof", cur.documents());
        assertEquals("https://forms.example.org/apply/1", cur.applicationUrl());
        assertTrue(cur.evidence().contains("deadline=2026-10-26"));
        assertEquals("2025-26", c.get(1).academicYear(), "en dash years are normalised");
    }

    @Test
    void aPageWithoutProgrammeDataYieldsNothing_neverInventedValues() {
        assertTrue(BuddyBrandExtractor.extract(TestPages.emptyShell(), "u").isEmpty());
        assertTrue(BuddyBrandExtractor.extract("<html>no json</html>", "u").isEmpty());
        assertTrue(BuddyBrandExtractor.extract("<script id=\"__NEXT_DATA__\">{not json</script>", "u").isEmpty());
        assertTrue(BuddyBrandExtractor.extract(null, "u").isEmpty());
    }

    @Test
    void missingDeadlineStaysNull_notDefaulted() {
        SchemeCandidate c = BuddyBrandExtractor.extract(TestPages.brandPage(TestPages.item(9, "Programme 2026-27", null, null, "2026", "x")), "u").get(0);
        assertNull(c.deadline());
        assertNull(c.openDate());
    }

    // ---- plain HTML pages -----------------------------------------------------------------------

    private static SchemeCandidate html(String body, String title) {
        String h = "<html><head><title>" + title + "</title></head><body><h1>" + title.split(" \\| ")[0] + "</h1><p>" + body
                + "</p><a href=\"/apply-now\">Apply now</a></body></html>";
        return HtmlPageExtractor.extract(h, org.jsoup.Jsoup.parse(h).text(), "https://example.org/scheme", TODAY).get(0);
    }

    @Test
    void explicitClosedNoticeIsRecordedWithItsWording() {
        SchemeCandidate c = html("Applications to the Program 2026-27 are now closed. Eligibility follows.", "Siemens Scholarship Program | Siemens");
        assertEquals(SchemeStatusCalculator.CLOSED_NOTICE, c.windowState());
        assertTrue(c.windowEvidence().contains("now closed"));
        assertEquals("Siemens Scholarship Program", c.name());
        assertEquals("2026-27", c.academicYear());
        assertNull(c.deadline());
        assertEquals("https://example.org/apply-now", c.applicationUrl());
    }

    @Test
    void rollingNoticeIsRecognised_butConflictingNoticesAreNot() {
        assertEquals(SchemeStatusCalculator.ROLLING_OPEN, html("Applications are accepted on a rolling basis.", "Open Grant").windowState());
        assertNull(html("Applications are accepted on a rolling basis. Applications are now closed for this cycle.", "Odd Grant").windowState());
        assertNull(html("Welcome to the scheme.", "Plain").windowState());
    }

    @Test
    void exactlyOneDateNearDeadlineWordingBecomesTheDeadline() {
        SchemeCandidate c = html("Last date to apply: 26 October 2026. Published on 1 September 2026.", "Scheme X");
        assertEquals(LocalDate.of(2026, 10, 26), c.deadline());
        assertTrue(c.evidence().startsWith("Page text:"));
    }

    @Test
    void severalDatesMakeThePageAmbiguous_soNoDeadlineIsChosen() {
        SchemeCandidate c = html("Last date for fresh applications is 26 October 2026. Last date for renewals is 30 November 2026.", "Scheme Y");
        assertNull(c.deadline());
        assertTrue(c.evidence().startsWith("AMBIGUOUS"));
    }

    @Test
    void hyphenatedNamesAreNotCutByTheSiteSuffixRule() {
        assertEquals("Merit-cum-Means Scholarship", html("text", "Merit-cum-Means Scholarship").name());
        assertEquals("Merit-cum-Means Scholarship", html("text", "Merit-cum-Means Scholarship | Portal").name());
    }

    // ---- status with window states --------------------------------------------------------------

    private static SchemeStatusCalculator.Result status(LocalDate open, LocalDate deadline, LocalDate verified, String window) {
        return SchemeStatusCalculator.compute("VERIFIED", open, deadline, verified, window, TODAY);
    }

    @Test
    void windowNoticesDecideOnlyWhenThereAreNoDatesAndTheyAreVerified() {
        LocalDate checked = TODAY.minusDays(1);
        assertEquals(SchemeStatus.OPEN, status(null, null, checked, SchemeStatusCalculator.ROLLING_OPEN).status());
        assertEquals(SchemeStatus.CLOSED, status(null, null, checked, SchemeStatusCalculator.CLOSED_NOTICE).status());
        assertEquals(SchemeStatus.UNKNOWN, status(null, null, null, SchemeStatusCalculator.ROLLING_OPEN).status(), "no verification date, no claim");
        assertEquals(SchemeStatus.UNKNOWN, status(null, null, checked, null).status());
    }

    @Test
    void aClosedNoticeContradictingAFutureDeadlineIsUnknown_notOpen() {
        SchemeStatusCalculator.Result r = status(null, TODAY.plusDays(10), TODAY, SchemeStatusCalculator.CLOSED_NOTICE);
        assertEquals(SchemeStatus.UNKNOWN, r.status());
        assertTrue(r.note().contains("conflict"));
    }

    @Test
    void verifiedDatesTakePrecedenceOverARollingNotice() {
        assertEquals(SchemeStatus.CLOSED, status(null, TODAY.minusDays(3), TODAY.minusDays(20), SchemeStatusCalculator.ROLLING_OPEN).status());
    }
}
