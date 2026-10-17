package com.scholarship.controller;

import com.scholarship.dto.ScholarshipDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.SourceSnapshotEntity;
import com.scholarship.service.AdminIngestionService;
import com.scholarship.service.ScholarshipReviewService;
import com.scholarship.service.SourceMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Ingestion Controller", description = "Automated URL & PDF Extraction, Review, and Approval APIs")
public class AdminScholarshipController {

    private final com.scholarship.service.ScholarshipIngestionService scholarshipIngestionService;
    private final SourceMonitorService sourceMonitorService;
    private final ScholarshipReviewService reviewService;

    public AdminScholarshipController(com.scholarship.service.ScholarshipIngestionService scholarshipIngestionService,
                                      SourceMonitorService sourceMonitorService, ScholarshipReviewService reviewService) {
        this.scholarshipIngestionService = scholarshipIngestionService;
        this.sourceMonitorService = sourceMonitorService;
        this.reviewService = reviewService;
    }

    /** Dates a reviewer checked against the official source. */
    public record VerifiedDatesRequest(
            LocalDate deadline,
            @JsonProperty("open_date") LocalDate openDate,
            @JsonProperty("academic_year") String academicYear,
            String note) {
    }

    @PostMapping(value = "/ingest/source")
    @Operation(
            summary = "Extract and Ingest Scholarship from Official URL or PDF Guideline",
            description = "Accepts a government scholarship URL (Jsoup/Playwright) or Multipart PDF (Apache PDFBox). " +
                    "Extracts raw text, structures columns and descriptive chunks via Groq (or heuristics when no API key is configured), and persists as PENDING_REVIEW."
    )
    public ResponseEntity<ScholarshipDto> ingestSource(
            @Parameter(description = "Official scholarship portal URL")
            @RequestParam(value = "sourceUrl", required = false) String sourceUrl,
            @Parameter(description = "Official guideline PDF file")
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        ScholarshipDto dto = scholarshipIngestionService.ingestSource(sourceUrl, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/scholarships/{id}/approve")
    @Operation(
            summary = "Approve Scholarship and Index in pgvector",
            description = "Generates 768-dimensional vector embeddings, indexes chunks into pgvector vector_store, and marks status as VERIFIED."
    )
    public ResponseEntity<ScholarshipDto> approveScholarship(
            @Parameter(description = "Scholarship ID to approve")
            @PathVariable Long id
    ) {
        ScholarshipDto approved = scholarshipIngestionService.approveScholarship(id);
        return ResponseEntity.ok(approved);
    }

    @GetMapping("/scholarships/pending")
    @Operation(
            summary = "List All Pending Review Scholarships",
            description = "Retrieves all newly extracted scholarships that are awaiting administrative approval."
    )
    public ResponseEntity<List<ScholarshipDto>> getPendingScholarships() {
        List<ScholarshipDto> pending = scholarshipIngestionService.getPendingScholarships();
        return ResponseEntity.ok(pending);
    }

    @GetMapping("/sources")
    @Operation(summary = "Registered official sources and their last fetch state")
    public ResponseEntity<List<SourceSnapshotEntity>> sources() {
        return ResponseEntity.ok(sourceMonitorService.snapshots());
    }

    @PostMapping("/sources/check")
    @Operation(
            summary = "Check all registered official sources now",
            description = "Fetches each source, detects content changes and flags affected schemes for review. "
                    + "A failed fetch never changes a scheme's dates or status. The same check runs on a schedule."
    )
    public ResponseEntity<List<SourceMonitorService.CheckResult>> checkSources() {
        return ResponseEntity.ok(sourceMonitorService.checkAll());
    }

    @GetMapping("/scholarships/review")
    @Operation(summary = "Schemes flagged for review (source changed, source unreachable, ...)")
    public ResponseEntity<List<ScholarshipDto>> flagged() {
        return ResponseEntity.ok(reviewService.flagged());
    }

    @GetMapping("/scholarships/{id}/deadline-history")
    @Operation(summary = "Applied and proposed deadlines for a scheme")
    public ResponseEntity<List<DeadlineHistoryEntity>> deadlineHistory(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.deadlineHistory(id));
    }

    @PostMapping("/scholarships/{id}/verified-dates")
    @Operation(
            summary = "Record verified application dates",
            description = "Use only after checking the official source. Stamps the dates as verified today, which is what lets the scheme show OPEN/CLOSED/UPCOMING instead of UNKNOWN."
    )
    public ResponseEntity<ScholarshipDto> verifiedDates(@PathVariable Long id, @RequestBody VerifiedDatesRequest body) {
        return ResponseEntity.ok(reviewService.applyVerifiedDates(id, body.openDate(), body.deadline(), body.academicYear(), body.note()));
    }

    @PostMapping("/scholarships/{id}/review/clear")
    @Operation(summary = "Clear a review flag after checking the scheme")
    public ResponseEntity<ScholarshipDto> clearReview(@PathVariable Long id) {
        return ResponseEntity.ok(reviewService.clearReview(id));
    }
}
