package com.scholarship.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One row per deadline change or proposal for a scheme. APPLIED rows changed the live record; PROPOSED rows await review. */
@Entity
@Table(name = "scholarship_deadline_history", indexes = @Index(name = "idx_deadline_history_scholarship", columnList = "scholarship_id"))
public class DeadlineHistoryEntity {

    public static final String APPLIED = "APPLIED";
    public static final String PROPOSED = "PROPOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scholarship_id", nullable = false)
    private Long scholarshipId;

    @Column(name = "previous_deadline")
    private LocalDate previousDeadline;

    private LocalDate deadline;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    /** APPLIED or PROPOSED. */
    @Column(length = 20, nullable = false)
    private String state;

    /** DATASET, ADMIN or SOURCE_CHECK. */
    @Column(length = 30, nullable = false)
    private String origin;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt = OffsetDateTime.now();

    public DeadlineHistoryEntity() {
    }

    public DeadlineHistoryEntity(Long scholarshipId, LocalDate previousDeadline, LocalDate deadline, String academicYear,
                                 String state, String origin, String note) {
        this.scholarshipId = scholarshipId;
        this.previousDeadline = previousDeadline;
        this.deadline = deadline;
        this.academicYear = academicYear;
        this.state = state;
        this.origin = origin;
        this.note = note;
    }

    public Long getId() { return id; }
    public Long getScholarshipId() { return scholarshipId; }
    public LocalDate getPreviousDeadline() { return previousDeadline; }
    public LocalDate getDeadline() { return deadline; }
    public String getAcademicYear() { return academicYear; }
    public String getState() { return state; }
    public String getOrigin() { return origin; }
    public String getNote() { return note; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
}
