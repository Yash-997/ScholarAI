package com.scholarship.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.dto.IngestionResponseDto;
import com.scholarship.dto.ScholarshipDto;
import com.scholarship.dto.ScholarshipIngestionDto;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.exception.ResourceNotFoundException;
import com.scholarship.repository.ScholarshipRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enterprise Ingestion Service for Pan-India State Portals & Private Corporate CSR Trusts.
 * Automates extraction via Jsoup (static), Playwright (dynamic JS SPAs), and Apache PDFBox (official guidelines).
 * Structures criteria into deterministic database rules using Groq (LLM) and indexes 768-dim semantic chunks into pgvector.
 */
@Service
public class ScholarshipIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipIngestionService.class);

    private final ScholarshipRepository scholarshipRepository;
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ScholarshipUpsertService upsertService;
    private final LlmAvailability llm;
    private final Clock clock;

    public ScholarshipIngestionService(
            ScholarshipRepository scholarshipRepository,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectMapper objectMapper,
            ScholarshipUpsertService upsertService,
            LlmAvailability llm,
            Clock clock
    ) {
        this.scholarshipRepository = scholarshipRepository;
        this.upsertService = upsertService;
        this.llm = llm;
        this.clock = clock;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        ChatModel chatModel = null;
        try {
            chatModel = chatModelProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("ChatModel initialization notice in ingestion service: {}", e.getMessage());
        }
        this.chatClient = chatModel != null ? ChatClient.create(chatModel) : null;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts and stages scholarship criteria from official URL or uploaded PDF guidelines.
     * Records are staged with status PENDING_REVIEW until approved by an administrator.
     */
    @Transactional
    public ScholarshipDto ingestSource(String sourceUrl, MultipartFile pdfFile) {
        String rawText = "";
        String originUrl = sourceUrl;

        // 1. Extract raw text from PDF or URL
        if (pdfFile != null && !pdfFile.isEmpty()) {
            log.info("Extracting text from uploaded PDF: {}", pdfFile.getOriginalFilename());
            try (InputStream is = pdfFile.getInputStream()) {
                rawText = extractTextFromPdf(is);
                if (originUrl == null || originUrl.isBlank()) {
                    originUrl = "Uploaded PDF: " + pdfFile.getOriginalFilename();
                }
            } catch (Exception e) {
                log.error("Failed to extract text from PDF: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to extract text from uploaded PDF: " + e.getMessage());
            }
        } else if (sourceUrl != null && !sourceUrl.isBlank()) {
            log.info("Extracting text from source URL: {}", sourceUrl);
            rawText = extractTextFromUrl(sourceUrl);
        } else {
            throw new IllegalArgumentException("Either an official source URL or a Multipart PDF file must be provided.");
        }

        if (rawText.isBlank()) {
            throw new RuntimeException("No extractable textual content found in the provided source.");
        }

        // Limit context window to first ~12,000 characters
        String truncatedText = rawText.length() > 12000 ? rawText.substring(0, 12000) : rawText;

        // 2. Parse extracted criteria via Groq (with heuristic fallback)
        ScholarshipEntity entity = structureRawTextWithGroq(truncatedText, originUrl);
        String key = com.scholarship.entity.DedupeKeys.forName(entity.getName());
        List<ScholarshipEntity> duplicates = key == null ? List.of() : scholarshipRepository.findByDedupeKey(key);
        if (!duplicates.isEmpty()) {
            ScholarshipEntity existing = duplicates.get(0);
            existing.setNeedsReview(true);
            existing.setReviewReason("A new extraction from " + originUrl + " matched this scheme and was not stored as a duplicate. Compare it against this record.");
            scholarshipRepository.save(existing);
            throw new IllegalArgumentException("Scheme '" + existing.getName() + "' already exists (id " + existing.getId()
                    + "); not creating a duplicate. It was flagged for review instead.");
        }
        entity.setVerificationStatus("PENDING_REVIEW");
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        ScholarshipEntity saved = scholarshipRepository.save(entity);
        log.info("Successfully staged scholarship '{}' (ID: {}) with status PENDING_REVIEW", saved.getName(), saved.getId());

        return mapToDto(saved);
    }

    /**
     * Approves a staged scholarship: generates 3 semantic chunks, indexes them into pgvector, and marks status as VERIFIED.
     */
    @Transactional
    public ScholarshipDto approveScholarship(Long id) {
        ScholarshipEntity entity = scholarshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with ID: " + id));

        if (entity.getExternalKey() != null && (isBlank(entity.getState()) || isBlank(entity.getCourse())
                || isBlank(entity.getCategoryCriteria()) || isBlank(entity.getGenderRequirements()))) {
            // Discovered records carry no structured criteria; an empty column would mean "matches every student".
            throw new IllegalArgumentException("Set state, course, category_criteria and gender_requirements (use ALL where unrestricted) "
                    + "from the official eligibility text before approving a discovered scheme.");
        }
        entity.setVerificationStatus("VERIFIED");
        entity.setLastVerified(LocalDate.now(clock));
        entity.setUpdatedAt(OffsetDateTime.now());

        ScholarshipEntity approved = scholarshipRepository.save(entity);
        indexSingleScholarship(approved);
        return mapToDto(approved);
    }

    /**
     * Lists all scholarships awaiting review.
     */
    @Transactional(readOnly = true)
    public List<ScholarshipDto> getPendingScholarships() {
        return scholarshipRepository.findByVerificationStatus("PENDING_REVIEW")
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Extracts text using Apache PDFBox 3.x
     */
    public String extractTextFromPdf(InputStream inputStream) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extracts text from URL using Playwright (if dynamic) or Jsoup
     * Supports NSP, MahaDBT, Karnataka SSP, UP Scholarship, Digital Gujarat, Bihar PMS, AP Jnanabhumi,
     * and private CSR programs (Siemens, Cummins, Reliance, Tata, Infosys, Amazon, Sitaram Jindal, Legrand).
     */
    public String extractTextFromUrl(String url) {
        // Fast Jsoup extraction
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .timeout(15000)
                    .get();

            StringBuilder sb = new StringBuilder();
            sb.append("Title: ").append(doc.title()).append("\n\n");

            doc.select("h1, h2, h3, h4, p, table tr, ul li").forEach(el -> {
                String text = el.text().trim();
                if (!text.isEmpty()) {
                    sb.append(text).append("\n");
                }
            });

            String content = sb.toString();
            if (content.length() > 250) {
                log.info("Jsoup extracted {} characters from {}", content.length(), url);
                return content;
            }
        } catch (Exception e) {
            log.warn("Jsoup extraction attempt encountered issue ({}). Checking dynamic renderer.", e.getMessage());
        }

        // Dynamic Playwright extraction for JavaScript-heavy portals
        try {
            return extractWithPlaywright(url);
        } catch (Throwable t) {
            log.warn("Playwright extraction failed or browser not installed: {}. Using basic HTTP text.", t.getMessage());
            try {
                return Jsoup.connect(url).ignoreHttpErrors(true).get().text();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to extract webpage content: " + ex.getMessage());
            }
        }
    }

    private String extractWithPlaywright(String url) {
        try (com.microsoft.playwright.Playwright playwright = com.microsoft.playwright.Playwright.create()) {
            com.microsoft.playwright.BrowserType.LaunchOptions options = new com.microsoft.playwright.BrowserType.LaunchOptions()
                    .setHeadless(true);
            try (com.microsoft.playwright.Browser browser = playwright.chromium().launch(options)) {
                com.microsoft.playwright.Page page = browser.newPage();
                page.navigate(url);
                page.waitForTimeout(3000); // allow JS tables and SPAs to render
                return page.innerText("body");
            }
        }
    }

    /**
     * Parses unstructured scholarship text via Groq into structured columns and descriptions.
     * Uses deterministic regex heuristics as a fallback.
     */
    private ScholarshipEntity structureRawTextWithGroq(String rawText, String sourceUrl) {
        ScholarshipEntity entity = new ScholarshipEntity();
        entity.setSourceUrl(sourceUrl);

        String systemPrompt = """
            You are an expert AI parser for Indian government and private scholarship schemes (MahaDBT, NSP, AICTE, CSR).
            Extract the structured scholarship attributes from the provided raw text and return ONLY a valid JSON object.
            Do not include Markdown backticks or code blocks. Return plain JSON.
            
            Required JSON structure:
            {
              "name": "Full Official Scholarship Title",
              "provider": "Department, Ministry, or Trust",
              "scheme_type": "State Government, Central Government, or Corporate CSR",
              "maximum_family_income": 800000.00,
              "minimum_marks_or_cgpa": 60.0,
              "course": "B.Tech / Undergraduate Technical",
              "state": "Maharashtra or All India",
              "category_criteria": "All, OBC, SC, ST, EBC",
              "gender_requirements": "All, Female, Male",
              "benefits": "Summary of tuition waiver, allowance, maintenance",
              "deadline": "YYYY-MM-DD",
              "required_documents": "Comma-separated list of required certificates",
              "application_url": "Direct portal URL or https://scholarships.gov.in/"
            }
            """;

        if (chatClient != null && llm.isConfigured()) {
            try {
                String aiResponse = chatClient.prompt()
                        .system(systemPrompt)
                        .user("Extract scholarship details from this official text:\n\n" + rawText)
                        .call()
                        .content();

                if (aiResponse != null && !aiResponse.isBlank()) {
                    String cleanJson = aiResponse.trim();
                    if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
                    else if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3);
                    if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
                    cleanJson = cleanJson.trim();

                    JsonNode root = objectMapper.readTree(cleanJson);
                    entity.setName(root.path("name").asText("Extracted Scholarship Scheme"));
                    entity.setProvider(root.path("provider").asText("Official Government or Trust Department"));
                    entity.setSchemeType(root.path("scheme_type").asText("Government Scheme"));

                    if (root.hasNonNull("maximum_family_income")) {
                        try {
                            BigDecimal inc = new BigDecimal(root.path("maximum_family_income").asText().replaceAll("[^0-9.]", ""));
                            entity.setMaximumFamilyIncome(inc);
                            entity.setIncomeLimit(inc);
                        } catch (Exception ignored) {}
                    }

                    if (root.hasNonNull("minimum_marks_or_cgpa")) {
                        try {
                            entity.setMinimumMarksOrCgpa(root.path("minimum_marks_or_cgpa").asDouble());
                        } catch (Exception ignored) {}
                    }

                    entity.setCourse(root.path("course").asText("Undergraduate / Engineering"));
                    entity.setState(root.path("state").asText("All India"));
                    entity.setCategoryCriteria(root.path("category_criteria").asText("All Categories"));
                    entity.setGenderRequirements(root.path("gender_requirements").asText("All"));
                    entity.setBenefits(root.path("benefits").asText("Financial assistance as per official guidelines"));

                    if (root.hasNonNull("deadline")) {
                        try {
                            entity.setDeadline(LocalDate.parse(root.path("deadline").asText()));
                        } catch (Exception ignored) {}
                    }

                    entity.setRequiredDocuments(root.path("required_documents").asText("Income Certificate, Marksheet, Domicile, Aadhaar"));
                    entity.setApplicationUrl(root.path("application_url").asText(sourceUrl));
                    entity.setEligibility("Structured via automated knowledge base ingestion");

                    return entity;
                }
            } catch (Exception e) {
                log.warn("Groq structuring call notice: {}. Applying regex heuristic parsing.", e.getMessage());
            }
        }

        // Heuristic fallback parser
        return applyHeuristicParsing(rawText, sourceUrl);
    }

    private ScholarshipEntity applyHeuristicParsing(String text, String sourceUrl) {
        ScholarshipEntity entity = new ScholarshipEntity();
        entity.setSourceUrl(sourceUrl);
        entity.setApplicationUrl(sourceUrl);

        // Name
        String[] lines = text.split("\n");
        String candidateName = "Official Scholarship Scheme";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 10 && trimmed.length() < 120 &&
                    (trimmed.toLowerCase().contains("scholarship") || trimmed.toLowerCase().contains("yojna") || trimmed.toLowerCase().contains("fellowship"))) {
                candidateName = trimmed;
                break;
            }
        }
        entity.setName(candidateName);
        entity.setProvider("Department of Higher & Technical Education");
        entity.setSchemeType(sourceUrl.contains("gov.in") ? "State Government" : "Corporate CSR");

        // Income Limit
        Pattern incomePattern = Pattern.compile("(?:income|income limit|annual income|family income)[^0-9]{1,20}(?:rs\\.?|inr)?\\s*([0-9,]+(?:\\.[0-9]{2})?)", Pattern.CASE_INSENSITIVE);
        Matcher incomeMatcher = incomePattern.matcher(text);
        if (incomeMatcher.find()) {
            try {
                String cleanNumber = incomeMatcher.group(1).replace(",", "");
                BigDecimal income = new BigDecimal(cleanNumber);
                if (income.compareTo(new BigDecimal("10000")) > 0) {
                    entity.setMaximumFamilyIncome(income);
                    entity.setIncomeLimit(income);
                }
            } catch (Exception ignored) {}
        }
        if (entity.getMaximumFamilyIncome() == null) {
            if (text.toLowerCase().contains("8 lakh") || text.toLowerCase().contains("8,00,000")) {
                entity.setMaximumFamilyIncome(new BigDecimal("800000.00"));
                entity.setIncomeLimit(new BigDecimal("800000.00"));
            } else if (text.toLowerCase().contains("2.5 lakh") || text.toLowerCase().contains("2,50,000")) {
                entity.setMaximumFamilyIncome(new BigDecimal("250000.00"));
                entity.setIncomeLimit(new BigDecimal("250000.00"));
            }
        }

        // Marks / CGPA
        Pattern marksPattern = Pattern.compile("([0-9]{2}(?:\\.[0-9]+)?)\\s*%", Pattern.CASE_INSENSITIVE);
        Matcher marksMatcher = marksPattern.matcher(text);
        if (marksMatcher.find()) {
            try {
                double marks = Double.parseDouble(marksMatcher.group(1));
                if (marks >= 40.0 && marks <= 100.0) {
                    entity.setMinimumMarksOrCgpa(marks);
                }
            } catch (Exception ignored) {}
        }

        // State detection
        String textLower = text.toLowerCase();
        if (textLower.contains("maharashtra")) entity.setState("Maharashtra");
        else if (textLower.contains("karnataka")) entity.setState("Karnataka");
        else if (textLower.contains("gujarat")) entity.setState("Gujarat");
        else if (textLower.contains("uttar pradesh")) entity.setState("Uttar Pradesh");
        else if (textLower.contains("bihar")) entity.setState("Bihar");
        else if (textLower.contains("andhra pradesh")) entity.setState("Andhra Pradesh");
        else entity.setState("All India");

        // Category criteria
        List<String> categories = new ArrayList<>();
        if (textLower.contains("sc") || textLower.contains("scheduled caste")) categories.add("SC");
        if (textLower.contains("st") || textLower.contains("scheduled tribe")) categories.add("ST");
        if (textLower.contains("obc") || textLower.contains("other backward")) categories.add("OBC");
        if (textLower.contains("ebc") || textLower.contains("economically backward")) categories.add("EBC");
        if (textLower.contains("general") || textLower.contains("open")) categories.add("Open");
        entity.setCategoryCriteria(categories.isEmpty() ? "All Categories" : String.join(", ", categories));

        // Gender requirements
        if (textLower.contains("girl") || textLower.contains("female") || textLower.contains("women")) {
            entity.setGenderRequirements("Female");
        } else {
            entity.setGenderRequirements("All");
        }

        entity.setCourse("B.E. / B.Tech / Undergraduate Technical");
        entity.setBenefits("Tuition fee reimbursement and monthly maintenance allowances as per official portal guidelines.");
        entity.setRequiredDocuments("Income Certificate, Domicile Certificate, Previous Marksheet, Caste Certificate (if applicable), Aadhaar Card");
        entity.setEligibility("Evaluated via automated portal extraction");

        return entity;
    }

    /**
     * Splits descriptive text into 3 targeted semantic segments for pgvector indexing.
     */
    public List<Document> createSemanticChunks(ScholarshipEntity scholarship) {
        List<Document> documents = new ArrayList<>();
        Map<String, Object> baseMetadata = new HashMap<>();
        baseMetadata.put("scholarship_id", scholarship.getId());
        baseMetadata.put("scholarship_name", scholarship.getName() != null ? scholarship.getName() : "");
        baseMetadata.put("name", scholarship.getName() != null ? scholarship.getName() : "");
        baseMetadata.put("provider", scholarship.getProvider() != null ? scholarship.getProvider() : "");
        baseMetadata.put("state", scholarship.getState() != null ? scholarship.getState() : "All India");
        baseMetadata.put("course", scholarship.getCourse() != null ? scholarship.getCourse() : "");
        baseMetadata.put("category", scholarship.getCategoryCriteria() != null ? scholarship.getCategoryCriteria() : "");
        baseMetadata.put("gender", scholarship.getGenderRequirements() != null ? scholarship.getGenderRequirements() : "All");
        // Provenance travels with every chunk so retrieved evidence can be cited and dated.
        baseMetadata.put("source_url", scholarship.getDeadlineSourceUrl() != null ? scholarship.getDeadlineSourceUrl()
                : (scholarship.getSourceUrl() != null ? scholarship.getSourceUrl() : ""));
        baseMetadata.put("fetched_at", scholarship.getDeadlineFetchedAt() != null ? scholarship.getDeadlineFetchedAt().toString() : "");
        baseMetadata.put("verified_on", scholarship.getDeadlineVerifiedOn() != null ? scholarship.getDeadlineVerifiedOn().toString() : "");
        baseMetadata.put("last_verified", scholarship.getLastVerified() != null ? scholarship.getLastVerified().toString() : "");
        baseMetadata.put("academic_year", scholarship.getAcademicYear() != null ? scholarship.getAcademicYear() : "");
        baseMetadata.put("deadline_origin", scholarship.getDeadlineOrigin() != null ? scholarship.getDeadlineOrigin() : "");

        // Chunk 1: Eligibility criteria and deterministic thresholds
        String chunk1 = String.format(
                "Scholarship: %s. Provider: %s. State: %s. Course: %s. Category: %s. Gender requirements: %s. Eligibility description: %s. Maximum annual family income limit: %s INR. Minimum required academic marks/CGPA: %s.",
                scholarship.getName(),
                scholarship.getProvider() != null ? scholarship.getProvider() : "Official Authority",
                scholarship.getState() != null ? scholarship.getState() : "All India",
                scholarship.getCourse() != null ? scholarship.getCourse() : "All Courses",
                scholarship.getCategoryCriteria() != null ? scholarship.getCategoryCriteria() : "All Categories",
                scholarship.getGenderRequirements() != null ? scholarship.getGenderRequirements() : "All",
                scholarship.getEligibility() != null ? scholarship.getEligibility() : "See official guidelines",
                scholarship.getMaximumFamilyIncome() != null ? scholarship.getMaximumFamilyIncome().toPlainString() : "No limit specified",
                scholarship.getMinimumMarksOrCgpa() != null ? String.valueOf(scholarship.getMinimumMarksOrCgpa()) : "No minimum specified"
        );
        Map<String, Object> meta1 = new HashMap<>(baseMetadata);
        meta1.put("chunk_type", "eligibility");
        documents.add(new Document(chunk1, meta1));

        // Chunk 2: Benefits, coverage, and financial assistance
        if (scholarship.getBenefits() != null && !scholarship.getBenefits().isBlank()) {
            String chunk2 = String.format(
                    "Scholarship: %s. Financial benefits and allowances: %s.",
                    scholarship.getName(),
                    scholarship.getBenefits()
            );
            Map<String, Object> meta2 = new HashMap<>(baseMetadata);
            meta2.put("chunk_type", "benefits");
            documents.add(new Document(chunk2, meta2));
        }

        // Chunk 3: Required documents, deadline, and application procedure
        StringBuilder chunk3 = new StringBuilder();
        chunk3.append("Scholarship: ").append(scholarship.getName()).append(". ");
        if (scholarship.getRequiredDocuments() != null && !scholarship.getRequiredDocuments().isBlank()) {
            chunk3.append("Required application documents: ").append(scholarship.getRequiredDocuments()).append(". ");
        }
        if (scholarship.getAcademicYear() != null) {
            chunk3.append("Academic year: ").append(scholarship.getAcademicYear()).append(". ");
        }
        if (scholarship.getOpenDate() != null) {
            chunk3.append("Applications open: ").append(scholarship.getOpenDate()).append(". ");
        }
        if (scholarship.getDeadline() != null) {
            chunk3.append("Application deadline: ").append(scholarship.getDeadline())
                    .append(scholarship.getDeadlineVerifiedOn() != null ? " (verified " + scholarship.getDeadlineVerifiedOn() + ")" : " (reported, not verified)")
                    .append(". ");
        }
        if (SchemeStatusCalculator.ROLLING_OPEN.equals(scholarship.getWindowState())) {
            chunk3.append("The official page states applications are open on a rolling basis. ");
        } else if (SchemeStatusCalculator.CLOSED_NOTICE.equals(scholarship.getWindowState())) {
            chunk3.append("The official page states applications are closed. ");
        }
        if (scholarship.getApplicationUrl() != null && !scholarship.getApplicationUrl().isBlank()) {
            chunk3.append("Application portal URL: ").append(scholarship.getApplicationUrl()).append(". ");
        }
        if (scholarship.getSourceUrl() != null && !scholarship.getSourceUrl().isBlank()) {
            chunk3.append("Official verification source: ").append(scholarship.getSourceUrl()).append(".");
        }
        Map<String, Object> meta3 = new HashMap<>(baseMetadata);
        meta3.put("chunk_type", "documents_and_procedure");
        documents.add(new Document(chunk3.toString(), meta3));

        return documents;
    }

    /** Replaces the scheme's chunks, so re-indexing after an update never leaves duplicates behind. */
    public void indexSingleScholarship(ScholarshipEntity scholarship) {
        if (vectorStore == null) return;
        removeFromIndex(scholarship.getId());
        List<Document> chunks = createSemanticChunks(scholarship);
        try {
            vectorStore.add(chunks);
            log.info("Indexed {} chunks for scholarship '{}' (ID: {})", chunks.size(), scholarship.getName(), scholarship.getId());
        } catch (Exception e) {
            log.warn("VectorStore indexing notice for scholarship {}: {}", scholarship.getId(), e.getMessage());
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    public void removeFromIndex(Long scholarshipId) {
        if (vectorStore == null || scholarshipId == null) return;
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("scholarship_id", scholarshipId).build());
        } catch (Exception e) {
            log.warn("Could not remove existing chunks for scholarship {}: {}", scholarshipId, e.getMessage());
        }
    }

    /** Idempotent: posting the same scheme twice updates the existing row (matched by normalised name). */
    @Transactional
    public IngestionResponseDto ingestScholarships(List<ScholarshipIngestionDto> dtoList) {
        List<Long> savedIds = new ArrayList<>();
        int chunkCount = 0;

        for (ScholarshipIngestionDto dto : dtoList) {
            ScholarshipEntity in = new ScholarshipEntity();
            in.setName(dto.name());
            in.setProvider(dto.provider());
            in.setSchemeType(dto.schemeType());
            in.setEligibility(dto.eligibility());
            in.setMaximumFamilyIncome(dto.maximumFamilyIncome());
            in.setMinimumMarksOrCgpa(dto.minimumMarksOrCgpa());
            in.setCourse(dto.course());
            in.setState(dto.state());
            in.setCategoryCriteria(dto.categoryCriteria());
            in.setGenderRequirements(dto.genderRequirements());
            in.setBenefits(dto.benefits());
            in.setDeadline(dto.deadline());   // reported, not verified: status stays UNKNOWN until a reviewer verifies it
            in.setRequiredDocuments(dto.requiredDocuments());
            in.setApplicationUrl(dto.applicationUrl());
            in.setSourceUrl(dto.sourceUrl());
            in.setVerificationStatus(dto.verificationStatus());

            ScholarshipUpsertService.Result res = upsertService.upsert(in, "ADMIN");
            ScholarshipEntity saved = res.entity();
            savedIds.add(saved.getId());
            if ("VERIFIED".equals(saved.getVerificationStatus())) {
                indexSingleScholarship(saved);
                chunkCount += createSemanticChunks(saved).size();
            }
        }

        return new IngestionResponseDto(
                savedIds.size(),
                chunkCount,
                "SUCCESS",
                "Ingested " + savedIds.size() + " scholarships (existing schemes updated in place) and indexed " + chunkCount + " chunks.",
                savedIds
        );
    }

    public List<Document> createChunksForScholarship(ScholarshipEntity scholarship) {
        return createSemanticChunks(scholarship);
    }

    private ScholarshipDto mapToDto(ScholarshipEntity entity) {
        return ScholarshipMapper.toDto(entity, LocalDate.now(clock));
    }
}
