package com.scholarship.service;

import com.scholarship.dto.ChatMessageDto;
import com.scholarship.dto.ScholarshipChatRequestDto;
import com.scholarship.dto.ScholarshipChatResponseDto;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.ScholarshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.scholarship.service.TestSchemes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScholarshipChatServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 17);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T06:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private ScholarshipRepository repo;
    private LlmGateway llm;
    private ScholarshipChatService chat;

    private ScholarshipEntity pragati;
    private ScholarshipEntity tata;
    private ScholarshipEntity cummins;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        pragati = scheme(7, "AICTE Pragati Scholarship for Girl Students");
        pragati.setGenderRequirements("Female");
        pragati.setCourse("Engineering;Diploma;Pharmacy");
        pragati.setMaximumFamilyIncome(java.math.BigDecimal.valueOf(800000));
        pragati.setRequiredDocuments("Income Certificate;Class 10 and 12 Marksheet;Admission Letter");
        pragati.setApplicationUrl("https://scholarships.gov.in/");
        pragati.setLastVerified(LocalDate.of(2026, 10, 17));

        tata = scheme(20, "Tata Capital Pankh Scholarship");
        minMarks(tata, 60);
        dates(tata, null, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 17));
        tata.setApplicationUrl("https://www.buddy4study.com/page/tata-capital-pankh-scholarship");

        cummins = scheme(23, "Cummins India Scholarship Programme");
        cummins.setState("Maharashtra;Jharkhand");
        cummins.setDeadline(LocalDate.of(2026, 9, 30));   // reported only, never verified
        cummins.setApplicationUrl("https://www.cummins.com/in");

        repo = mock(ScholarshipRepository.class);
        when(repo.findByVerificationStatus("VERIFIED")).thenReturn(List.of(pragati, tata, cummins));
        ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vs = mock(ObjectProvider.class);
        llm = mock(LlmGateway.class);
        chat = new ScholarshipChatService(repo, new EligibilityService(repo, CLOCK), vs, llm, CLOCK, new RetrievalInfo());
    }

    private static ScholarshipChatRequestDto ask(String message, ChatMessageDto... history) {
        ScholarshipChatRequestDto r = new ScholarshipChatRequestDto(message);
        r.setHistory(List.of(history));
        return r;
    }

    private void llmUnavailable(LlmGateway.Failure why) {
        when(llm.complete(anyString(), anyString())).thenReturn(new LlmGateway.Reply(null, why));
    }

    // ---- fallbacks -----------------------------------------------------------------------------

    @Test
    void withoutCredentials_answersFromRecords_andSaysSo() {
        llmUnavailable(LlmGateway.Failure.NOT_CONFIGURED);
        ScholarshipChatResponseDto r = chat.chat(ask("What documents are needed for Pragati?"));
        assertEquals("FALLBACK", r.getMode());
        assertEquals("NOT_CONFIGURED", r.getFallbackReason());
        assertTrue(r.getReply().contains("Income Certificate"), r.getReply());
        assertTrue(r.getReply().contains("https://scholarships.gov.in/"));
        assertTrue(r.getReply().contains("not configured"), "must disclose that the model was not used");
        assertEquals("keyword", r.getRetrievalMode());
        assertEquals(List.of("AICTE Pragati Scholarship for Girl Students"), r.getSourceCitations());
    }

    @Test
    void timeoutAndErrorAndEmptyAllFallBackWithTheirReason() {
        for (LlmGateway.Failure f : List.of(LlmGateway.Failure.TIMEOUT, LlmGateway.Failure.ERROR, LlmGateway.Failure.EMPTY)) {
            llmUnavailable(f);
            ScholarshipChatResponseDto r = chat.chat(ask("benefits of Pragati scholarship"));
            assertEquals("FALLBACK", r.getMode());
            assertEquals(f.name(), r.getFallbackReason());
            assertTrue(r.getReply().contains("Rs. 10,000 per year"), r.getReply());
        }
    }

    @Test
    void malformedModelOutputIsRejectedInFavourOfRecords() {
        when(llm.complete(anyString(), anyString())).thenReturn(new LlmGateway.Reply("{\"error\": {\"message\": \"bad gateway\"}}", null));
        ScholarshipChatResponseDto r = chat.chat(ask("Tell me about Tata Pankh"));
        assertEquals("FALLBACK", r.getMode());
        assertEquals("REJECTED", r.getFallbackReason());
        assertTrue(r.getReply().contains("Tata Capital Pankh Scholarship"));
    }

    @Test
    void whenNothingMatches_itAdmitsThatAndDoesNotCallTheModel() {
        ScholarshipChatResponseDto r = chat.chat(ask("tell me about the Moon Landing Grant"));
        verify(llm, never()).complete(anyString(), anyString());
        assertEquals("NO_RECORDS", r.getFallbackReason());
        assertTrue(r.getReply().contains("would rather not guess"));
        assertTrue(r.getReply().contains("https://scholarships.gov.in/"));
        assertTrue(r.getSources().isEmpty());
    }

    // ---- grounding -----------------------------------------------------------------------------

    @Test
    void modelReplyIsKept_butFabricatedLinksAreStripped_andStatusFooterIsAppendedByCode() {
        when(llm.complete(anyString(), anyString())).thenReturn(new LlmGateway.Reply(
                "Tata Pankh [S1] looks right for you. Apply at https://tata-fake-portal.example/apply or https://www.buddy4study.com/page/tata-capital-pankh-scholarship .", null));
        ScholarshipChatResponseDto r = chat.chat(ask("Tell me about Tata Pankh"));
        assertEquals("LLM", r.getMode());
        assertNull(r.getFallbackReason());
        assertFalse(r.getReply().contains("tata-fake-portal"), r.getReply());
        assertTrue(r.getReply().contains(ReplyGuard.OMITTED));
        assertTrue(r.getReply().contains("https://www.buddy4study.com/page/tata-capital-pankh-scholarship"));
        assertTrue(r.getReply().contains("**OPEN**"), "status footer comes from the record, not the model: " + r.getReply());
        assertTrue(r.getReply().contains("Last verified"));
    }

    @Test
    void promptContainsRecordFactsAndMarksUnverifiedDeadlinesAsSuch() {
        when(llm.complete(anyString(), anyString())).thenReturn(new LlmGateway.Reply("Here is what I know about Cummins [S1].", null));
        chat.chat(ask("When is the Cummins scholarship deadline?"));
        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(system.capture(), user.capture());
        assertTrue(user.getValue().contains("reported, NOT verified"), user.getValue());
        assertTrue(user.getValue().contains("UNKNOWN"));
        assertTrue(user.getValue().contains("TODAY: 2026-10-17"));
        assertTrue(system.getValue().contains("Never guess"));
    }

    @Test
    void unverifiedDeadlineIsNotPresentedAsClosedInTheFallbackEither() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        String reply = chat.chat(ask("What is the deadline for Cummins?")).getReply();
        assertTrue(reply.contains("UNKNOWN"), reply);
        assertTrue(reply.contains("NOT verified"), reply);
        assertFalse(reply.contains("CLOSED"), reply);
    }

    // ---- personalisation and follow-ups --------------------------------------------------------

    @Test
    void recommendsOnlySchemesTheStudentMatches_andExplainsWhy() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        // Male student with 55% marks: Pragati (female only) and Tata (min 60) must not be offered.
        ScholarshipChatResponseDto r = chat.chat(ask("Which scholarships can I get? I am a boy from Maharashtra with 55% marks and income 3 lakh, doing B.Tech"));
        assertEquals(List.of("Cummins India Scholarship Programme"), r.getSourceCitations());
        assertTrue(r.getReply().contains("state Maharashtra"));
        assertTrue(r.getReply().contains("domicile Maharashtra is covered"), r.getReply());
        assertEquals("Male", r.getUnderstoodProfile().get("gender"));
        assertEquals(55.0, r.getUnderstoodProfile().get("marks"));
    }

    @Test
    void raisingMarksOnlyAddsSchemes_loweringOnlyRemoves() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        List<String> high = chat.chat(ask("Which scholarships can I get? girl, Maharashtra, 90% marks, income 3 lakh, engineering")).getSourceCitations();
        List<String> low = chat.chat(ask("Which scholarships can I get? girl, Maharashtra, 50% marks, income 3 lakh, engineering")).getSourceCitations();
        assertTrue(high.containsAll(low), "high=" + high + " low=" + low);
        assertTrue(high.contains("Tata Capital Pankh Scholarship"));
        assertFalse(low.contains("Tata Capital Pankh Scholarship"));
    }

    @Test
    void followUpQuestionResolvesTheSchemeFromEarlierTurns() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        ScholarshipChatResponseDto r = chat.chat(ask("what documents do I need for it?",
                new ChatMessageDto("user", "Is there a scholarship for girls in engineering?"),
                new ChatMessageDto("assistant", "Yes - the AICTE Pragati Scholarship for Girl Students offers Rs. 50,000 per year.")));
        assertEquals(List.of("AICTE Pragati Scholarship for Girl Students"), r.getSourceCitations());
        assertTrue(r.getReply().contains("Admission Letter"), r.getReply());
    }

    @Test
    void profileSharedEarlierInTheConversationIsRemembered() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        ScholarshipChatResponseDto r = chat.chat(ask("am I eligible for Pragati?",
                new ChatMessageDto("user", "I'm a girl, doing B.Tech, family income 9 lakh")));
        assertTrue(r.getReply().contains("does not match"), "9 lakh is above the 8 lakh ceiling: " + r.getReply());
        assertTrue(r.getReply().contains("family income 900000"), r.getReply());
    }

    @Test
    void explicitProfileFromTheEligibilityFormIsUsed() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        ScholarshipChatRequestDto req = ask("which scholarships am I eligible for?");
        req.setProfile(new com.scholarship.dto.RecommendRequestDto(null, java.math.BigDecimal.valueOf(300000), 80.0, "Maharashtra", "General", "Engineering", "Female"));
        ScholarshipChatResponseDto r = chat.chat(req);
        assertTrue(r.getSourceCitations().contains("AICTE Pragati Scholarship for Girl Students"));
        assertEquals("Maharashtra", r.getUnderstoodProfile().get("state"));
    }

    // ---- input hygiene -------------------------------------------------------------------------

    @Test
    void blankMessageGetsAGreeting_andNeverReachesTheModel() {
        ScholarshipChatResponseDto r = chat.chat(ask("   "));
        verifyNoInteractions(llm);
        assertTrue(r.getReply().contains("I only state what is on record"));
    }

    @Test
    void nullSenderAndHugeInputsDoNotBreakIt() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        ChatMessageDto odd = new ChatMessageDto(null, "x".repeat(50_000));
        ScholarshipChatResponseDto r = chat.chat(ask("Pragati documents? " + "y".repeat(50_000), odd, odd, odd, odd, odd, odd, odd, odd));
        assertNotNull(r.getReply());
    }

    @Test
    void sourcesCarryTheProvenanceOfTheDates() {
        tata.setAcademicYear("2026-27");
        tata.setDeadlineOrigin("SOURCE_AUTO");
        tata.setDeadlineSourceUrl("https://www.buddy4study.com/page/the-tata-capital-pankh-scholarship-programme");
        tata.setDeadlineFetchedAt(java.time.OffsetDateTime.parse("2026-10-17T10:00:00+05:30"));
        llmUnavailable(LlmGateway.Failure.ERROR);
        ScholarshipChatResponseDto.Source src = chat.chat(ask("Tell me about Tata Pankh")).getSources().get(0);
        assertEquals("2026-27", src.academicYear());
        assertEquals("SOURCE_AUTO", src.deadlineOrigin());
        assertEquals(LocalDate.of(2026, 10, 26), src.deadline());
        assertTrue(src.sourceUrl().contains("buddy4study"));
        assertNotNull(src.fetchedAt());
    }

    @Test
    void theModelIsToldWhereTheDatesCameFrom() {
        tata.setDeadlineOrigin("SOURCE_AUTO");
        tata.setDeadlineSourceUrl("https://www.buddy4study.com/page/x");
        tata.setDeadlineFetchedAt(java.time.OffsetDateTime.parse("2026-10-17T10:00:00+05:30"));
        when(llm.complete(anyString(), anyString())).thenReturn(new LlmGateway.Reply("Tata Pankh [S1] is open.", null));
        chat.chat(ask("Tell me about Tata Pankh"));
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).complete(anyString(), user.capture());
        assertTrue(user.getValue().contains("Dates came from: the official/partner page, read automatically on 2026-10-17"), user.getValue());
    }

    @Test
    void closedSchemesAreNotRecommended_butCanStillBeAskedAbout() {
        dates(tata, null, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 1));   // verified and passed
        llmUnavailable(LlmGateway.Failure.ERROR);
        List<String> recommended = chat.chat(ask("Which scholarships can I get? girl, Maharashtra, 90% marks, income 3 lakh, engineering")).getSourceCitations();
        assertFalse(recommended.contains("Tata Capital Pankh Scholarship"));
        String asked = chat.chat(ask("What is the deadline for Tata Pankh?")).getReply();
        assertTrue(asked.contains("CLOSED"), asked);
    }

    @Test
    void retrievalModeReflectsTheConfiguredEmbeddingModel() {
        llmUnavailable(LlmGateway.Failure.ERROR);
        assertEquals("keyword", chat.chat(ask("Tell me about Tata Pankh")).getRetrievalMode());
        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vs = mock(ObjectProvider.class);
        ScholarshipChatService semantic = new ScholarshipChatService(repo, new EligibilityService(repo, CLOCK), vs, llm, CLOCK,
                new RetrievalInfo("openai-compatible", ""));
        assertEquals("semantic", semantic.chat(ask("Tell me about Tata Pankh")).getRetrievalMode());
    }

    @Test
    void intentDetection() {
        assertEquals(ScholarshipChatService.Intent.DOCUMENTS, ScholarshipChatService.detectIntent("what documents are required", false));
        assertEquals(ScholarshipChatService.Intent.DEADLINE, ScholarshipChatService.detectIntent("when does it close? deadline?", true));
        assertEquals(ScholarshipChatService.Intent.BENEFITS, ScholarshipChatService.detectIntent("how much money do I get", true));
        assertEquals(ScholarshipChatService.Intent.RECOMMEND, ScholarshipChatService.detectIntent("which scholarships can I get", false));
        assertEquals(ScholarshipChatService.Intent.ELIGIBILITY, ScholarshipChatService.detectIntent("am I eligible for Pragati", true));
        assertEquals(ScholarshipChatService.Intent.APPLY, ScholarshipChatService.detectIntent("how do I apply for Pragati", true));
    }
}
