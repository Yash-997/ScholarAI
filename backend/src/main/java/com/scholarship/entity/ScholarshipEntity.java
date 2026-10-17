package com.scholarship.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "scholarships")
public class ScholarshipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String provider;

    @Column(name = "scheme_type", columnDefinition = "TEXT")
    private String schemeType;

    @Column(columnDefinition = "TEXT")
    private String eligibility;

    @Column(name = "income_limit", precision = 12, scale = 2)
    private BigDecimal incomeLimit;

    @Column(name = "maximum_family_income", precision = 12, scale = 2)
    private BigDecimal maximumFamilyIncome;

    @Column(name = "minimum_marks_or_cgpa")
    private Double minimumMarksOrCgpa;

    @Column(columnDefinition = "TEXT")
    private String course;

    @Column(columnDefinition = "TEXT")
    private String state;

    @Column(name = "year_of_study", length = 50)
    private String yearOfStudy;

    @Column(name = "category_criteria", columnDefinition = "TEXT")
    private String categoryCriteria;

    @Column(name = "gender_requirements", columnDefinition = "TEXT")
    private String genderRequirements;

    @Column(columnDefinition = "TEXT")
    private String benefits;

    private LocalDate deadline;

    @Column(name = "required_documents", columnDefinition = "TEXT")
    private String requiredDocuments;

    @Column(name = "application_url", columnDefinition = "TEXT")
    private String applicationUrl;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "last_verified")
    private LocalDate lastVerified;

    @Column(name = "verification_status", length = 50)
    private String verificationStatus;

    /** First day applications open, when known. Only trusted together with deadline_verified_on. */
    @Column(name = "open_date")
    private LocalDate openDate;

    /** Date a human or the dataset registry verified {@code openDate}/{@code deadline}; null means unverified. */
    @Column(name = "deadline_verified_on")
    private LocalDate deadlineVerifiedOn;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    /** Last time the registered source for this scheme was fetched successfully. */
    @Column(name = "last_checked")
    private OffsetDateTime lastChecked;

    /** Null is treated as false so the column can be added to a populated table. */
    @Column(name = "needs_review")
    private Boolean needsReview;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    /** Normalised name used to prevent duplicate rows. */
    @Column(name = "dedupe_key", columnDefinition = "TEXT")
    private String dedupeKey;

    /** Registry id (source_registry.csv) of the official source this scheme is tracked against. */
    @Column(name = "source_id", length = 20)
    private String sourceId;

    /** ROLLING_OPEN or CLOSED_NOTICE: an explicit statement on an official page, with {@link #windowEvidence}. */
    @Column(name = "window_state", length = 20)
    private String windowState;

    @Column(name = "window_evidence", columnDefinition = "TEXT")
    private String windowEvidence;

    /** Page the verified dates / window state were read from, and when it was fetched. */
    @Column(name = "deadline_source_url", columnDefinition = "TEXT")
    private String deadlineSourceUrl;

    @Column(name = "deadline_fetched_at")
    private OffsetDateTime deadlineFetchedAt;

    /** DATASET, ADMIN or SOURCE_AUTO. A SOURCE_AUTO refresh never overwrites ADMIN-verified dates. */
    @Column(name = "deadline_origin", length = 20)
    private String deadlineOrigin;

    /** Stable id from a structured source (for example "b4s:26637") so re-discovery updates instead of duplicating. */
    @Column(name = "external_key", length = 120)
    private String externalKey;

    /** Hash of the dataset row last applied, so unchanged rows are skipped and manual edits survive restarts. */
    @Column(name = "seed_hash", length = 64)
    private String seedHash;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public ScholarshipEntity() {
    }

    @PrePersist
    public void onPrePersist() {
        this.dedupeKey = DedupeKeys.forName(this.name);
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = OffsetDateTime.now();
        }
        if (this.verificationStatus == null) {
            this.verificationStatus = "VERIFIED";
        }
        if (this.maximumFamilyIncome == null && this.incomeLimit != null) {
            this.maximumFamilyIncome = this.incomeLimit;
        }
        if (this.incomeLimit == null && this.maximumFamilyIncome != null) {
            this.incomeLimit = this.maximumFamilyIncome;
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        this.dedupeKey = DedupeKeys.forName(this.name);
        this.updatedAt = OffsetDateTime.now();
        if (this.maximumFamilyIncome == null && this.incomeLimit != null) {
            this.maximumFamilyIncome = this.incomeLimit;
        }
        if (this.incomeLimit == null && this.maximumFamilyIncome != null) {
            this.incomeLimit = this.maximumFamilyIncome;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getSchemeType() {
        return schemeType;
    }

    public void setSchemeType(String schemeType) {
        this.schemeType = schemeType;
    }

    public String getEligibility() {
        return eligibility;
    }

    public void setEligibility(String eligibility) {
        this.eligibility = eligibility;
    }

    public BigDecimal getIncomeLimit() {
        return incomeLimit != null ? incomeLimit : maximumFamilyIncome;
    }

    public void setIncomeLimit(BigDecimal incomeLimit) {
        this.incomeLimit = incomeLimit;
        if (this.maximumFamilyIncome == null) {
            this.maximumFamilyIncome = incomeLimit;
        }
    }

    public BigDecimal getMaximumFamilyIncome() {
        return maximumFamilyIncome != null ? maximumFamilyIncome : incomeLimit;
    }

    public void setMaximumFamilyIncome(BigDecimal maximumFamilyIncome) {
        this.maximumFamilyIncome = maximumFamilyIncome;
        if (this.incomeLimit == null) {
            this.incomeLimit = maximumFamilyIncome;
        }
    }

    public Double getMinimumMarksOrCgpa() {
        return minimumMarksOrCgpa;
    }

    public void setMinimumMarksOrCgpa(Double minimumMarksOrCgpa) {
        this.minimumMarksOrCgpa = minimumMarksOrCgpa;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(String yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }

    public String getCategoryCriteria() {
        return categoryCriteria;
    }

    public void setCategoryCriteria(String categoryCriteria) {
        this.categoryCriteria = categoryCriteria;
    }

    public String getGenderRequirements() {
        return genderRequirements;
    }

    public void setGenderRequirements(String genderRequirements) {
        this.genderRequirements = genderRequirements;
    }

    public String getBenefits() {
        return benefits;
    }

    public void setBenefits(String benefits) {
        this.benefits = benefits;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public String getRequiredDocuments() {
        return requiredDocuments;
    }

    public void setRequiredDocuments(String requiredDocuments) {
        this.requiredDocuments = requiredDocuments;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
        this.applicationUrl = applicationUrl;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public LocalDate getLastVerified() {
        return lastVerified;
    }

    public void setLastVerified(LocalDate lastVerified) {
        this.lastVerified = lastVerified;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDate getOpenDate() { return openDate; }
    public void setOpenDate(LocalDate openDate) { this.openDate = openDate; }

    public LocalDate getDeadlineVerifiedOn() { return deadlineVerifiedOn; }
    public void setDeadlineVerifiedOn(LocalDate deadlineVerifiedOn) { this.deadlineVerifiedOn = deadlineVerifiedOn; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public OffsetDateTime getLastChecked() { return lastChecked; }
    public void setLastChecked(OffsetDateTime lastChecked) { this.lastChecked = lastChecked; }

    public boolean isNeedsReview() { return Boolean.TRUE.equals(needsReview); }
    public void setNeedsReview(Boolean needsReview) { this.needsReview = needsReview; }

    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getWindowState() { return windowState; }
    public void setWindowState(String windowState) { this.windowState = windowState; }

    public String getWindowEvidence() { return windowEvidence; }
    public void setWindowEvidence(String windowEvidence) { this.windowEvidence = windowEvidence; }

    public String getDeadlineSourceUrl() { return deadlineSourceUrl; }
    public void setDeadlineSourceUrl(String deadlineSourceUrl) { this.deadlineSourceUrl = deadlineSourceUrl; }

    public OffsetDateTime getDeadlineFetchedAt() { return deadlineFetchedAt; }
    public void setDeadlineFetchedAt(OffsetDateTime deadlineFetchedAt) { this.deadlineFetchedAt = deadlineFetchedAt; }

    public String getDeadlineOrigin() { return deadlineOrigin; }
    public void setDeadlineOrigin(String deadlineOrigin) { this.deadlineOrigin = deadlineOrigin; }

    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }

    public String getSeedHash() { return seedHash; }
    public void setSeedHash(String seedHash) { this.seedHash = seedHash; }

    // Alias methods for prompt naming consistency
    public String getEligibleStates() {
        return state;
    }

    public void setEligibleStates(String eligibleStates) {
        this.state = eligibleStates;
    }

    public String getCategoryRequirements() {
        return categoryCriteria;
    }

    public void setCategoryRequirements(String categoryRequirements) {
        this.categoryCriteria = categoryRequirements;
    }

    public String getEligibleCourse() {
        return course;
    }

    public void setEligibleCourse(String eligibleCourse) {
        this.course = eligibleCourse;
    }

    public String getOfficialApplicationUrl() {
        return applicationUrl;
    }

    public void setOfficialApplicationUrl(String officialApplicationUrl) {
        this.applicationUrl = officialApplicationUrl;
    }
}
