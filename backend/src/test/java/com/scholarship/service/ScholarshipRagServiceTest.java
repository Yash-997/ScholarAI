package com.scholarship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.dto.RecommendRequestDto;
import com.scholarship.dto.ScholarshipRecommendationDto;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static com.scholarship.service.TestSchemes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScholarshipRagServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    @SuppressWarnings("unchecked")
    private ScholarshipRagService service(List<ScholarshipEntity> schemes) {
        ScholarshipRepository repo = mock(ScholarshipRepository.class);
        when(repo.findByVerificationStatus("VERIFIED")).thenReturn(schemes);
        ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vs = mock(ObjectProvider.class); // returns null: no ranking index
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.isUsable()).thenReturn(false);
        return new ScholarshipRagService(new EligibilityService(repo, CLOCK), vs, llm, new ObjectMapper());
    }

    private static RecommendRequestDto request(double marks) {
        return new RecommendRequestDto("x", BigDecimal.valueOf(300000), marks, "Maharashtra", "General", "Engineering", "All", true);
    }

    private static RecommendRequestDto defaultRequest(double marks) {
        return new RecommendRequestDto("x", BigDecimal.valueOf(300000), marks, "Maharashtra", "General", "Engineering", "All");
    }

    private ScholarshipEntity scheme(long id) {
        return TestSchemes.scheme(id, "Scheme " + id);
    }

    @Test
    void parseReasons_acceptsArrayWrappedInProse_andDropsUnknownIds() {
        String raw = "Here you go:\n[{\"scholarship_id\":1,\"eligibility_reason\":\"Income fits.\"},"
                + "{\"scholarship_id\":99,\"eligibility_reason\":\"Hallucinated.\"}]";
        Map<Long, String> reasons = service(List.of()).parseReasons(raw, List.of(scheme(1), scheme(2)));
        assertEquals(Map.of(1L, "Income fits."), reasons);
    }

    @Test
    void parseReasons_returnsEmptyForNonJson() {
        ScholarshipRagService s = service(List.of());
        assertTrue(s.parseReasons("no json here", List.of(scheme(1))).isEmpty());
        assertTrue(s.parseReasons("[not json]", List.of(scheme(1))).isEmpty());
        assertTrue(s.parseReasons(null, List.of(scheme(1))).isEmpty());
    }

    @Test
    void normalizers_mapFormValuesToDatasetTokens() {
        assertNull(ScholarshipRagService.normalizeState("All India"));
        assertEquals("Karnataka", ScholarshipRagService.normalizeState(" Karnataka "));
        assertEquals("GEN", ScholarshipRagService.normalizeCategory("General / Open"));
        assertEquals("VJNT", ScholarshipRagService.normalizeCategory("VJNT / SBC"));
        assertEquals("SC", ScholarshipRagService.normalizeCategory("sc"));
        assertEquals("Engineering", ScholarshipRagService.normalizeCourse("B.Tech / Engineering"));
        assertEquals("Medical", ScholarshipRagService.normalizeCourse("MBBS / Medical"));
        assertEquals("Degree", ScholarshipRagService.normalizeCourse("B.Sc / General Degree"));
        assertNull(ScholarshipRagService.normalizeGender("All / Any"));
        assertEquals("Female", ScholarshipRagService.normalizeGender("Female"));
    }

    @Test
    void expiredScheme_isReturnedAsClosed_andSortedAfterOpenOnes() {
        ScholarshipEntity closed = dates(scheme(1), null, TODAY.minusDays(12), TODAY.minusDays(40));
        ScholarshipEntity open = dates(scheme(2), null, TODAY.plusDays(9), TODAY.minusDays(1));
        ScholarshipEntity unknown = scheme(3);
        List<ScholarshipRecommendationDto> out = service(List.of(closed, open, unknown)).recommend(request(90));
        assertEquals(List.of(2L, 3L, 1L), out.stream().map(ScholarshipRecommendationDto::scholarshipId).toList());
        assertEquals("OPEN", out.get(0).status());
        assertEquals("UNKNOWN", out.get(1).status());
        assertEquals("CLOSED", out.get(2).status());
        assertTrue(out.get(2).statusNote().contains("passed"));
    }

    @Test
    void closedSchemesAreHiddenByDefault_andOpenOnesStayProminent() {
        ScholarshipEntity closed = dates(scheme(1), null, TODAY.minusDays(12), TODAY.minusDays(40));
        ScholarshipEntity open = dates(scheme(2), null, TODAY.plusDays(9), TODAY.minusDays(1));
        ScholarshipEntity unknown = scheme(3);
        List<ScholarshipRecommendationDto> out = service(List.of(closed, open, unknown)).recommend(defaultRequest(90));
        assertEquals(List.of(2L, 3L), out.stream().map(ScholarshipRecommendationDto::scholarshipId).toList());
        assertEquals("OPEN", out.get(0).status());
    }

    @Test
    void eligibilityIsNotTheSameAsAnOpenWindow() {
        // Eligible but with an unverified date: still returned (eligible), and never labelled OPEN.
        ScholarshipEntity s = dates(scheme(1), null, TODAY.plusDays(30), null);
        ScholarshipRecommendationDto dto = service(List.of(s)).recommend(defaultRequest(90)).get(0);
        assertEquals("UNKNOWN", dto.status());
        // Open window but the student fails a rule: not returned at all.
        ScholarshipEntity strict = minMarks(dates(scheme(2), null, TODAY.plusDays(30), TODAY.minusDays(1)), 95);
        assertTrue(service(List.of(strict)).recommend(defaultRequest(90)).isEmpty());
    }

    @Test
    void provenanceOfDatesIsReturned() {
        ScholarshipEntity s = dates(scheme(1), null, TODAY.plusDays(9), TODAY);
        s.setAcademicYear("2026-27");
        s.setDeadlineOrigin("SOURCE_AUTO");
        s.setDeadlineSourceUrl("https://www.buddy4study.com/page/x");
        ScholarshipRecommendationDto dto = service(List.of(s)).recommend(defaultRequest(90)).get(0);
        assertEquals("2026-27", dto.academicYear());
        assertEquals("SOURCE_AUTO", dto.deadlineOrigin());
        assertEquals("https://www.buddy4study.com/page/x", dto.deadlineSourceUrl());
    }

    @Test
    void unverifiedDeadlineInThePast_isNotReportedAsClosed() {
        ScholarshipEntity s = dates(scheme(1), null, TODAY.minusDays(30), null);
        ScholarshipRecommendationDto dto = service(List.of(s)).recommend(defaultRequest(90)).get(0);
        assertEquals("UNKNOWN", dto.status());
        assertNull(dto.deadlineVerifiedOn());
    }

    @Test
    void nonVerifiedRecordsNeverReachTheStudent() {
        ScholarshipEntity archived = scheme(1);
        archived.setVerificationStatus("ARCHIVED_LEGACY");
        // The repository only hands out VERIFIED rows; this documents that the service does not widen that.
        assertTrue(service(List.of()).recommend(request(90)).isEmpty());
        assertEquals(SchemeStatus.UNVERIFIED, SchemeStatusCalculator.compute(archived, TODAY).status());
    }

    @Test
    void loweringMarks_neverAddsASchemeToTheRecommendations() {
        List<ScholarshipEntity> schemes = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) schemes.add(minMarks(scheme(i), 35 + i * 3));
        ScholarshipRagService svc = service(schemes);
        List<Long> previous = null;
        for (double m = 100; m >= 35; m -= 1) {
            List<Long> ids = svc.recommend(request(m)).stream().map(ScholarshipRecommendationDto::scholarshipId).toList();
            if (previous != null) assertTrue(previous.containsAll(ids), "marks " + m + " added " + ids);
            previous = ids;
        }
    }

    @Test
    void everyEligibleSchemeIsReturned_noSilentCap() {
        List<ScholarshipEntity> schemes = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) schemes.add(scheme(i));
        assertEquals(30, service(schemes).recommend(request(90)).size());
    }

    @Test
    void reasonIsDeterministicAndListsTheCriteriaThatWerePassed() {
        ScholarshipEntity s = maxIncome(minMarks(scheme(1), 80), 450000);
        ScholarshipRecommendationDto dto = service(List.of(s)).recommend(request(85)).get(0);
        assertTrue(dto.eligibilityReason().contains("marks 85% meet the 80% minimum"), dto.eligibilityReason());
        assertTrue(dto.eligibilityReason().contains("Rs. 450,000"), dto.eligibilityReason());
        assertFalse(dto.matchedCriteria().isEmpty());
    }
}
