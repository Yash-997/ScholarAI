package com.scholarship.service;

import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Single write path for scheme content. Matches on the normalised-name dedupe key, so re-running any ingestion
 * (dataset reload, JSON ingest, scheduled source check) updates a row in place instead of inserting a duplicate.
 * Every deadline change is recorded in the history table.
 */
@Service
public class ScholarshipUpsertService {

    private static final Logger log = LoggerFactory.getLogger(ScholarshipUpsertService.class);

    private final ScholarshipRepository repository;
    private final DeadlineHistoryRepository historyRepository;

    public ScholarshipUpsertService(ScholarshipRepository repository, DeadlineHistoryRepository historyRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    public record Result(ScholarshipEntity entity, boolean created, boolean deadlineChanged) {
    }

    /**
     * @param incoming transient entity carrying the new content (not persisted itself)
     * @param origin   DATASET, ADMIN or SOURCE_CHECK; stored in the deadline history
     */
    @Transactional
    public Result upsert(ScholarshipEntity incoming, String origin) {
        String key = com.scholarship.entity.DedupeKeys.forName(incoming.getName());
        if (key == null) {
            throw new IllegalArgumentException("Scholarship name is required");
        }
        List<ScholarshipEntity> matches = repository.findByDedupeKey(key);
        ScholarshipEntity target = matches.isEmpty() ? null : matches.get(0);
        if (matches.size() > 1) {
            log.warn("{} rows share dedupe key '{}'; updating id {} only. Remove the others manually.", matches.size(), key, target.getId());
        }

        boolean created = target == null;
        if (created) {
            target = new ScholarshipEntity();
            target.setCreatedAt(OffsetDateTime.now());
        }
        java.time.LocalDate previousDeadline = created ? null : target.getDeadline();
        copyContent(incoming, target);
        target.setUpdatedAt(OffsetDateTime.now());
        ScholarshipEntity saved = repository.save(target);

        boolean deadlineChanged = !Objects.equals(previousDeadline, saved.getDeadline()) && (!created || saved.getDeadline() != null);
        if (deadlineChanged) {
            historyRepository.save(new DeadlineHistoryEntity(saved.getId(), previousDeadline, saved.getDeadline(),
                    saved.getAcademicYear(), DeadlineHistoryEntity.APPLIED, origin,
                    saved.getDeadlineVerifiedOn() != null ? "Verified on " + saved.getDeadlineVerifiedOn() : "Not verified"));
        }
        return new Result(saved, created, deadlineChanged);
    }

    private static void copyContent(ScholarshipEntity from, ScholarshipEntity to) {
        to.setName(from.getName());
        to.setProvider(from.getProvider());
        to.setSchemeType(from.getSchemeType());
        to.setEligibility(from.getEligibility());
        to.setMaximumFamilyIncome(from.getMaximumFamilyIncome());
        to.setIncomeLimit(from.getMaximumFamilyIncome());
        to.setMinimumMarksOrCgpa(from.getMinimumMarksOrCgpa());
        to.setCourse(from.getCourse());
        to.setState(from.getState());
        to.setYearOfStudy(from.getYearOfStudy());
        to.setCategoryCriteria(from.getCategoryCriteria());
        to.setGenderRequirements(from.getGenderRequirements());
        to.setBenefits(from.getBenefits());
        // Dates a source refresh or a reviewer already recorded survive an upsert that carries no dates of its own.
        boolean keepDates = from.getDeadline() == null && from.getOpenDate() == null
                && ("SOURCE_AUTO".equals(to.getDeadlineOrigin()) || "ADMIN".equals(to.getDeadlineOrigin()));
        if (!keepDates) {
            to.setDeadline(from.getDeadline());
            to.setOpenDate(from.getOpenDate());
            to.setDeadlineVerifiedOn(from.getDeadlineVerifiedOn());
            to.setAcademicYear(from.getAcademicYear());
            to.setDeadlineOrigin(from.getDeadline() != null || from.getOpenDate() != null ? from.getDeadlineOrigin() : null);
            to.setDeadlineSourceUrl(from.getDeadlineSourceUrl());
            to.setDeadlineFetchedAt(from.getDeadlineFetchedAt());
            to.setWindowState(from.getWindowState());
            to.setWindowEvidence(from.getWindowEvidence());
        }
        to.setRequiredDocuments(from.getRequiredDocuments());
        to.setApplicationUrl(from.getApplicationUrl());
        to.setSourceUrl(from.getSourceUrl());
        to.setSourceId(from.getSourceId());
        to.setLastVerified(from.getLastVerified());
        to.setSeedHash(from.getSeedHash());
        if (from.getVerificationStatus() != null) {
            to.setVerificationStatus(from.getVerificationStatus());
        }
    }
}
