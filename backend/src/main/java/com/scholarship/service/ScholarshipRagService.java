package com.scholarship.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.dto.RecommendRequestDto;
import com.scholarship.dto.ScholarshipRecommendationDto;
import com.scholarship.entity.ScholarshipEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Three-stage recommendation pipeline.
 * 1. Deterministic eligibility ({@link EligibilityService}): income, marks, state, category, course, gender. No AI.
 * 2. Keyword-overlap ranking of the survivors in the pgvector store. Ordering only; it can never add or remove a scheme.
 * 3. Optional LLM wording of the "why you qualify" sentence. Every factual field, the status and the matched criteria
 *    come from the database and the rules, never from the model.
 */
@Service
public class ScholarshipRagService {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipRagService.class);
    /** Safety valve only. The dataset is far smaller; a low cap would make results shift as filters change. */
    private static final int MAX_RESULTS = 200;
    private static final String DEFAULT_QUERY = "Scholarships this student qualifies for, with financial benefits.";

    private final EligibilityService eligibilityService;
    private final VectorStore vectorStore;
    private final LlmGateway llm;
    private final ObjectMapper objectMapper;

    public ScholarshipRagService(
            EligibilityService eligibilityService,
            ObjectProvider<VectorStore> vectorStoreProvider,
            LlmGateway llm,
            ObjectMapper objectMapper
    ) {
        this.eligibilityService = eligibilityService;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    public List<ScholarshipRecommendationDto> recommend(RecommendRequestDto request) {
        String query = (request.query() == null || request.query().isBlank()) ? DEFAULT_QUERY : request.query();

        // Stage 1: deterministic eligibility
        List<EligibilityService.Match> eligible = eligibilityService.match(EligibilityEvaluator.Profile.of(request));
        log.info("Stage 1: {} schemes passed the eligibility rules", eligible.size());
        if (!request.wantsClosed()) {
            // Eligibility says who qualifies; the application window says whether they can apply now. Closed schemes are opt-in.
            eligible = eligible.stream().filter(m -> m.status().status() != SchemeStatus.CLOSED).toList();
        }
        if (eligible.isEmpty()) {
            return List.of();
        }

        // Stage 2: keyword ranking within the eligible set, then usable schemes before CLOSED ones
        List<EligibilityService.Match> ranked = rank(query, eligible);

        // Stage 3: optional wording; deterministic fallback per scheme
        Map<Long, String> reasons = generateReasons(request, ranked);
        List<ScholarshipRecommendationDto> result = new ArrayList<>(ranked.size());
        for (EligibilityService.Match m : ranked) {
            ScholarshipEntity s = m.scheme();
            result.add(toDto(m, reasons.getOrDefault(s.getId(), deterministicReason(m))));
        }
        return result;
    }

    private List<EligibilityService.Match> rank(String query, List<EligibilityService.Match> eligible) {
        Map<Long, EligibilityService.Match> byId = new LinkedHashMap<>();
        eligible.forEach(m -> byId.put(m.scheme().getId(), m));
        List<Long> order = new ArrayList<>();

        if (vectorStore != null) {
            try {
                List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(query)
                        .topK(Math.max(10, byId.size() * 3))
                        .filterExpression(new FilterExpressionBuilder().in("scholarship_id", byId.keySet().toArray()).build())
                        .build());
                for (Document doc : hits) {
                    Object raw = doc.getMetadata().get("scholarship_id");
                    if (raw == null) continue;
                    try {
                        Long id = Long.valueOf(raw.toString());
                        if (byId.containsKey(id) && !order.contains(id)) order.add(id);
                    } catch (NumberFormatException ignored) {
                        // metadata without a numeric id is not a scheme chunk
                    }
                }
                log.info("Stage 2: keyword ranking placed {} of {} eligible schemes", order.size(), byId.size());
            } catch (Exception e) {
                log.warn("Stage 2 ranking failed, keeping database order: {}", e.getMessage());
            }
        }
        byId.keySet().stream().filter(id -> !order.contains(id)).forEach(order::add);
        // Stable sort by status group; relevance order is preserved inside each group.
        return order.stream().map(byId::get)
                .sorted(Comparator.comparingInt((EligibilityService.Match m) -> EligibilityService.statusRank(m.status().status())))
                .limit(MAX_RESULTS).toList();
    }

    private Map<Long, String> generateReasons(RecommendRequestDto req, List<EligibilityService.Match> candidates) {
        if (!llm.isUsable()) return Map.of();
        StringBuilder context = new StringBuilder();
        for (EligibilityService.Match m : candidates) {
            ScholarshipEntity s = m.scheme();
            context.append(String.format("ID %d | %s | Provider: %s | Benefits: %s | Criteria this student passed: %s%n",
                    s.getId(), s.getName(), s.getProvider(), s.getBenefits(), String.join("; ", m.why())));
        }
        String system = """
                You explain scholarship eligibility to Indian students. Every scheme listed has ALREADY passed
                a deterministic eligibility check, so do not question eligibility and never invent facts.
                Use only the criteria given for each scheme. Do not mention deadlines, dates or application status.
                Respond with ONLY a valid JSON array, no markdown and no other text. Each element must be exactly:
                {"scholarship_id": <number from the list>, "eligibility_reason": "<one or two sentences tying the
                student's profile to this scheme's criteria>"}
                Include one element per listed scheme.
                """;
        String user = String.format("""
                Student: income %s INR, marks %s%%, domicile %s, category %s, course %s, gender %s.

                Eligible schemes:
                %s""",
                orNotSpecified(req.annualFamilyIncome()), orNotSpecified(req.marks()), orNotSpecified(req.state()),
                orNotSpecified(req.category()), orNotSpecified(req.course()), orNotSpecified(req.gender()), context);

        LlmGateway.Reply reply = llm.complete(system, user);
        if (!reply.ok()) {
            log.warn("Stage 3 skipped, using deterministic reasons ({})", reply.failure());
            return Map.of();
        }
        List<ScholarshipEntity> schemes = candidates.stream().map(EligibilityService.Match::scheme).toList();
        return parseReasons(reply.text(), schemes);
    }

    /** Extracts the JSON array from the model output, keeping only ids that were actually offered. */
    Map<Long, String> parseReasons(String raw, List<ScholarshipEntity> candidates) {
        if (raw == null) return Map.of();
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            log.warn("Stage 3: model output contained no JSON array");
            return Map.of();
        }
        try {
            JsonNode array = objectMapper.readTree(raw.substring(start, end + 1));
            Set<Long> allowed = new HashSet<>();
            candidates.forEach(c -> allowed.add(c.getId()));
            Map<Long, String> reasons = new HashMap<>();
            for (JsonNode node : array) {
                long id = node.path("scholarship_id").asLong(-1);
                String reason = node.path("eligibility_reason").asText("").trim();
                if (allowed.contains(id) && !reason.isEmpty()) reasons.put(id, reason);
            }
            return reasons;
        } catch (Exception e) {
            log.warn("Stage 3: model output was not valid JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private ScholarshipRecommendationDto toDto(EligibilityService.Match m, String reason) {
        ScholarshipEntity s = m.scheme();
        List<String> docs = s.getRequiredDocuments() == null ? List.of()
                : Arrays.stream(s.getRequiredDocuments().split(";")).map(String::trim).filter(d -> !d.isEmpty()).toList();
        return new ScholarshipRecommendationDto(s.getId(), s.getName(), s.getProvider(), s.getBenefits(),
                reason, docs, s.getDeadline(), s.getApplicationUrl(),
                m.status().status().name(), m.status().note(), s.getDeadlineVerifiedOn(), s.getSourceUrl(),
                s.getLastVerified(), s.isNeedsReview(), m.why(),
                s.getAcademicYear(), s.getOpenDate(), s.getWindowState(), s.getWindowEvidence(),
                s.getDeadlineOrigin(), s.getDeadlineSourceUrl(), s.getDeadlineFetchedAt());
    }

    private String deterministicReason(EligibilityService.Match m) {
        if (m.why().isEmpty()) {
            return "This scheme has no restriction on the details you entered.";
        }
        return "You qualify because " + String.join(", ", m.why()) + ".";
    }

    private static String orNotSpecified(Object value) {
        return value == null ? "not specified" : value.toString();
    }

    // Kept as static delegates so existing callers and tests keep working.
    static String normalizeState(String state) { return EligibilityEvaluator.normalizeState(state); }
    static String normalizeGender(String gender) { return EligibilityEvaluator.normalizeGender(gender); }
    static String normalizeCategory(String category) { return EligibilityEvaluator.normalizeCategory(category); }
    static String normalizeCourse(String course) { return EligibilityEvaluator.normalizeCourse(course); }
}
