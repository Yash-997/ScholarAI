package com.scholarship.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scholarship.dto.IngestionResponseDto;
import com.scholarship.dto.RecommendRequestDto;
import com.scholarship.dto.ScholarshipRecommendationDto;
import com.scholarship.dto.ScholarshipDto;
import com.scholarship.dto.ScholarshipIngestionDto;
import com.scholarship.service.ScholarshipIngestionService;
import com.scholarship.service.ScholarshipRagService;
import com.scholarship.service.ScholarshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/scholarships")
@Tag(name = "Scholarship Controller", description = "Scholarship CRUD, Ingestion, and Multimodal AI Recommendation APIs")
public class ScholarshipController {

    private final ScholarshipService scholarshipService;
    private final ScholarshipIngestionService scholarshipIngestionService;
    private final ScholarshipRagService scholarshipRagService;
    private final com.scholarship.service.ScholarshipChatService scholarshipChatService;
    private final ObjectMapper objectMapper;

    public ScholarshipController(
            ScholarshipService scholarshipService,
            ScholarshipIngestionService scholarshipIngestionService,
            ScholarshipRagService scholarshipRagService,
            com.scholarship.service.ScholarshipChatService scholarshipChatService,
            ObjectMapper objectMapper
    ) {
        this.scholarshipService = scholarshipService;
        this.scholarshipIngestionService = scholarshipIngestionService;
        this.scholarshipRagService = scholarshipRagService;
        this.scholarshipChatService = scholarshipChatService;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingestion Service Endpoint: Accepts government scholarship data (JSON).
     * Stores deterministic rules (maximum_family_income, minimum_marks_or_cgpa) in PostgreSQL,
     * chunks descriptive text, generates embeddings, and stores them in VectorStore.
     */
    @PostMapping(value = "/ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Ingest Government Scholarship Data (JSON)",
            description = "Accepts government scholarship data as JSON array or single object. Idempotent: schemes are matched by normalised name and updated in place. Reported deadlines are stored as unverified.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Scholarship JSON records to ingest and vectorize",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ScholarshipIngestionDto.class),
                            examples = @ExampleObject(
                                    name = "Government Scholarship Ingestion Example",
                                    value = """
                                            [
                                              {
                                                "name": "Rajarshi Chhatrapati Shahu Maharaj Shikshan Shulkh Shishyavrutti Yojna (EBC)",
                                                "provider": "Directorate of Higher Education, Govt of Maharashtra",
                                                "scheme_type": "State Government",
                                                "maximum_family_income": 800000,
                                                "minimum_marks_or_cgpa": 50.0,
                                                "course": "Undergraduate Engineering (B.E./B.Tech), Professional Courses",
                                                "state": "Maharashtra",
                                                "category_criteria": "OPEN, General, EWS, SEBC",
                                                "benefits": "50% tuition fee and 50% exam fee reimbursement for professional degree courses.",
                                                "deadline": "2026-03-31",
                                                "required_documents": "Domicile Certificate, Income Certificate from Tehsildar, CAP Allotment Letter, Marksheets",
                                                "application_url": "https://mahadbt.maharashtra.gov.in/",
                                                "source_url": "https://mahadbt.maharashtra.gov.in/SchemeData/SchemeData?str=E9DDFA703C38E51A7F87664D908F8367"
                                              }
                                            ]
                                            """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Scholarships successfully ingested and vectorized"),
                    @ApiResponse(responseCode = "400", description = "Invalid payload format")
            }
    )
    public ResponseEntity<IngestionResponseDto> ingestScholarships(@RequestBody JsonNode payload) {
        List<ScholarshipIngestionDto> dtoList = new ArrayList<>();
        try {
            if (payload.isArray()) {
                dtoList = objectMapper.convertValue(payload, new TypeReference<List<ScholarshipIngestionDto>>() {});
            } else if (payload.isObject()) {
                ScholarshipIngestionDto single = objectMapper.convertValue(payload, ScholarshipIngestionDto.class);
                dtoList.add(single);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new IngestionResponseDto(
                    0, 0, "FAILED", "Invalid JSON format: " + e.getMessage(), List.of()
            ));
        }

        IngestionResponseDto response = scholarshipIngestionService.ingestScholarships(dtoList);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Three-stage RAG recommendation: SQL eligibility filter, vector ranking, LLM reasons.
     * Returns a bare JSON array of recommended schemes.
     */
    @PostMapping(value = "/recommend", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Recommend eligible scholarships",
            description = "Filters schemes deterministically (income, marks, state, category, course, gender), orders the survivors by application status and keyword relevance, and returns a JSON array. Each item carries its computed status (OPEN, CLOSED, UPCOMING, UNKNOWN, UNVERIFIED)."
    )
    public ResponseEntity<List<ScholarshipRecommendationDto>> recommendScholarships(@RequestBody RecommendRequestDto request) {
        return ResponseEntity.ok(scholarshipRagService.recommend(request));
    }
    @GetMapping
    @Operation(summary = "List Scholarships with Filters & Pagination", description = "Retrieves paginated scholarships with optional filtering by state, course, provider, and scheme type.")
    public ResponseEntity<Page<ScholarshipDto>> getScholarships(
            @Parameter(description = "Filter by state (e.g., Maharashtra)") @RequestParam(required = false) String state,
            @Parameter(description = "Filter by course (e.g., B.Tech)") @RequestParam(required = false) String course,
            @Parameter(description = "Filter by provider department") @RequestParam(required = false) String provider,
            @Parameter(description = "Filter by scheme type (State Government, Central Government)") @RequestParam(required = false) String schemeType,
            @Parameter(description = "Include schemes whose verified application window has closed (default false)") @RequestParam(defaultValue = "false") boolean includeClosed,
            @Parameter(description = "Only this status: OPEN, UPCOMING, CLOSED, UNKNOWN or UNVERIFIED") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,asc") String[] sort
    ) {
        String sortField = sort[0];
        Sort.Direction direction = (sort.length > 1 && sort[1].equalsIgnoreCase("desc")) ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<ScholarshipDto> results = scholarshipService.getScholarships(state, course, provider, schemeType, includeClosed, status, pageRequest);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/discovered")
    @Operation(summary = "Programmes discovered on partner pages, awaiting review",
            description = "Read from official/partner pages by the source monitor. Their criteria were parsed from free text and are not used by /recommend until a reviewer approves them.")
    public ResponseEntity<List<ScholarshipDto>> getDiscovered() {
        return ResponseEntity.ok(scholarshipService.getDiscovered());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Scholarship by ID", description = "Fetches a single scholarship record by its unique identifier.")
    public ResponseEntity<ScholarshipDto> getScholarshipById(@PathVariable Long id) {
        ScholarshipDto dto = scholarshipService.getScholarshipById(id);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    @Operation(summary = "Create Scholarship Record", description = "Creates a new scholarship record and triggers automatic vector indexing in VectorStore.")
    public ResponseEntity<ScholarshipDto> createScholarship(@Valid @RequestBody ScholarshipDto dto) {
        ScholarshipDto created = scholarshipService.createScholarship(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Scholarship Record", description = "Updates an existing scholarship record and triggers vector re-indexing.")
    public ResponseEntity<ScholarshipDto> updateScholarship(@PathVariable Long id, @Valid @RequestBody ScholarshipDto dto) {
        ScholarshipDto updated = scholarshipService.updateScholarship(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Scholarship Record", description = "Deletes a scholarship.")
    public ResponseEntity<Void> deleteScholarship(@PathVariable Long id) {
        scholarshipService.deleteScholarship(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/chat")
    @Operation(
            summary = "Conversational Scholarship AI Chatbot",
            description = "Conversational assistant grounded in the scholarship records in PostgreSQL. Retrieval is keyword-based, not semantic. Falls back to an answer composed from records when the AI model is unavailable."
    )
    public ResponseEntity<com.scholarship.dto.ScholarshipChatResponseDto> chat(
            @RequestBody com.scholarship.dto.ScholarshipChatRequestDto request
    ) {
        com.scholarship.dto.ScholarshipChatResponseDto response = scholarshipChatService.chat(request);
        return ResponseEntity.ok(response);
    }
}

