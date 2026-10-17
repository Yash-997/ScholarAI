package com.scholarship.service;

import com.scholarship.config.LexicalHashEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Profile extraction, reply guarding and the keyword vector model. */
class ChatSupportTest {

    private static final EligibilityEvaluator.Profile EMPTY = new EligibilityEvaluator.Profile(null, null, null, null, null, null);

    private static EligibilityEvaluator.Profile read(String... texts) {
        return ChatProfileExtractor.extract(EMPTY, List.of(texts));
    }

    // ---- ChatProfileExtractor ------------------------------------------------------------------

    @Test
    void readsIncomeInLakhAndRupees() {
        assertEquals(0, new BigDecimal("250000").compareTo(read("my family income is 2.5 lakh").income()));
        assertEquals(0, new BigDecimal("300000").compareTo(read("income 3 LPA").income()));
        assertEquals(0, new BigDecimal("180000").compareTo(read("we earn Rs. 1,80,000 a year").income()));
        assertEquals(0, new BigDecimal("450000").compareTo(read("annual income is 450000").income()));
        assertNull(read("I need a scholarship").income());
    }

    @Test
    void readsMarksButNotDisabilityPercent() {
        assertEquals(78.5, read("I scored 78.5%").marks());
        assertEquals(85.0, read("I got 85 percent in 12th").marks());
        assertNull(read("I have a 40% disability").marks());
        assertEquals(72.0, read("40% disability but I scored 72%").marks());
        assertNull(read("income is 12 lakh").marks());
    }

    @Test
    void readsStateCategoryGenderCourse() {
        EligibilityEvaluator.Profile p = read("I am a girl from Madhya Pradesh doing B.Tech, OBC category");
        assertEquals("Madhya Pradesh", p.state());
        assertEquals("Female", p.gender());
        assertEquals("Engineering", p.course());
        assertEquals("OBC", p.category());
        assertEquals("SC", read("I belong to SC").category());
        assertEquals("ST", read("scheduled tribe student").category());
        assertEquals("MINORITY", read("I am a muslim student").category());
        assertEquals("GEN", read("I am in the general category").category());
        assertNull(read("what is the general procedure to apply?").category(), "the word 'general' alone is not a category");
        assertNull(read("it costs a lot, so I need help").category(), "lower-case sc/st inside words must not count");
    }

    @Test
    void laterMessagesOverrideEarlierOnes() {
        EligibilityEvaluator.Profile p = read("I am from Bihar with 60%", "actually I scored 82% and live in Karnataka");
        assertEquals("Karnataka", p.state());
        assertEquals(82.0, p.marks());
    }

    @Test
    void unreadableInputLeavesEverythingNull() {
        EligibilityEvaluator.Profile p = read("hello", "what scholarships exist?");
        assertNull(p.income());
        assertNull(p.marks());
        assertNull(p.state());
        assertNull(p.category());
        assertNull(p.course());
        assertNull(p.gender());
    }

    // ---- ReplyGuard ----------------------------------------------------------------------------

    @Test
    void unknownUrlsAreRemovedAndKnownOnesKept() {
        Set<String> allowed = Set.of("https://scholarships.gov.in/", "https://example.gov.in/apply");
        String reply = "Apply at https://scholarships.gov.in. Also see https://totally-made-up.example/win-money, or www.example.gov.in/apply.";
        String out = ReplyGuard.stripUnknownUrls(reply, allowed);
        assertTrue(out.contains("https://scholarships.gov.in."), out);
        assertFalse(out.contains("made-up"), out);
        assertTrue(out.contains(ReplyGuard.OMITTED), out);
    }

    @Test
    void allowedLinksSurviveMarkdownWrapping() {
        Set<String> allowed = Set.of("https://scholarships.gov.in/");
        for (String wrapped : List.of("**https://scholarships.gov.in/**", "[Apply](https://scholarships.gov.in/)",
                "_https://scholarships.gov.in/_", "<https://scholarships.gov.in/>", "(https://scholarships.gov.in/).",
                "`https://scholarships.gov.in/`", "https://scholarships.gov.in")) {
            String out = ReplyGuard.stripUnknownUrls("Apply here: " + wrapped, allowed);
            assertFalse(out.contains(ReplyGuard.OMITTED), wrapped + " -> " + out);
        }
        assertTrue(ReplyGuard.stripUnknownUrls("**https://evil.example/x**", allowed).contains(ReplyGuard.OMITTED));
    }

    @Test
    void aDeeperInventedPathOnAnOfficialSiteIsNotTrusted() {
        String out = ReplyGuard.stripUnknownUrls("See https://scholarships.gov.in/secret/page123 now", Set.of("https://scholarships.gov.in/"));
        assertFalse(out.contains("page123"));
    }

    @Test
    void usabilityCheck() {
        assertFalse(ReplyGuard.looksUsable(null));
        assertFalse(ReplyGuard.looksUsable("ok"));
        assertFalse(ReplyGuard.looksUsable("{\"error\": {\"message\": \"rate limited\"}}"));
        assertTrue(ReplyGuard.looksUsable("You can apply for this scheme through the NSP portal."));
    }

    // ---- LexicalHashEmbeddingModel -------------------------------------------------------------

    private static double cosine(float[] a, float[] b) {
        double d = 0;
        for (int i = 0; i < a.length; i++) d += a[i] * b[i];
        return d;
    }

    @Test
    void tokenizerIgnoresPunctuationCaseAndPlurals() {
        assertEquals(List.of("scholarship", "scheme"), LexicalHashEmbeddingModel.tokens("Scholarships: the SCHEMES!"));
    }

    @Test
    void sharedVocabularyScoresHigherThanUnrelatedText() {
        LexicalHashEmbeddingModel m = new LexicalHashEmbeddingModel();
        float[] q = m.embed("documents required for AICTE Pragati scholarship");
        float[] related = m.embed("Scholarship: AICTE Pragati Scholarship for Girl Students. Required application documents: Income Certificate.");
        float[] unrelated = m.embed("Karnataka post matric hostel allowance for backward classes welfare department");
        assertTrue(cosine(q, related) > cosine(q, unrelated) + 0.1);
    }

    @Test
    void vectorsAreDeterministicAndUnitLength() {
        LexicalHashEmbeddingModel m = new LexicalHashEmbeddingModel();
        float[] a = m.embed("income certificate");
        assertArrayEquals(a, m.embed("income certificate"));
        assertEquals(1.0, cosine(a, a), 1e-5);
        assertEquals(768, a.length);
        assertEquals(0.0, cosine(m.embed(""), m.embed("")), 0.0, "empty text gives the zero vector");
    }

    @Test
    void itIsKeywordMatchingNotSemanticSearch() {
        // Documented limitation: synonyms with no shared words do not match. If this ever starts passing as
        // "similar", a real embedding model has been wired in and the docs/UI wording should be revisited.
        LexicalHashEmbeddingModel m = new LexicalHashEmbeddingModel();
        double synonyms = cosine(m.embed("scholarship for girls"), m.embed("fellowship for female candidates"));
        double sameWords = cosine(m.embed("scholarship for girls"), m.embed("scholarship for girls"));
        assertTrue(synonyms < 0.2, "synonym similarity was " + synonyms);
        assertEquals(1.0, sameWords, 1e-5);
    }
}
