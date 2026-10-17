package com.scholarship.service;

import com.scholarship.dto.ScholarshipDto;
import com.scholarship.entity.DeadlineHistoryEntity;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.exception.ResourceNotFoundException;
import com.scholarship.repository.DeadlineHistoryRepository;
import com.scholarship.repository.ScholarshipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** Human review actions: apply a verified deadline, clear a review flag. The only place a deadline becomes "verified". */
@Service
public class ScholarshipReviewService {

    private final ScholarshipRepository repository;
    private final DeadlineHistoryRepository history;
    private final Clock clock;

    public ScholarshipReviewService(ScholarshipRepository repository, DeadlineHistoryRepository history, Clock clock) {
        this.repository = repository;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Records dates a person has checked against the official source. Both dates are optional, but at least one is required.
     * Stamps deadline_verified_on with today's date, writes a history row and clears the review flag.
     */
    @Transactional
    public ScholarshipDto applyVerifiedDates(Long id, LocalDate openDate, LocalDate deadline, String academicYear, String note) {
        if (openDate == null && deadline == null) {
            throw new IllegalArgumentException("Provide a deadline and/or an open_date.");
        }
        if (openDate != null && deadline != null && deadline.isBefore(openDate)) {
            throw new IllegalArgumentException("deadline must not be before open_date.");
        }
        ScholarshipEntity s = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with ID: " + id));
        LocalDate today = LocalDate.now(clock);
        LocalDate previous = s.getDeadline();
        s.setOpenDate(openDate);
        s.setDeadline(deadline);
        if (academicYear != null && !academicYear.isBlank()) s.setAcademicYear(academicYear.trim());
        s.setDeadlineVerifiedOn(today);
        s.setLastVerified(today);
        s.setNeedsReview(false);
        s.setReviewReason(null);
        s.setUpdatedAt(OffsetDateTime.now(clock));
        repository.save(s);
        if (!Objects.equals(previous, deadline)) {
            history.save(new DeadlineHistoryEntity(s.getId(), previous, deadline, s.getAcademicYear(), DeadlineHistoryEntity.APPLIED,
                    "ADMIN", note == null || note.isBlank() ? "Verified by reviewer on " + today : note.trim()));
        }
        return ScholarshipMapper.toDto(s, today);
    }

    @Transactional
    public ScholarshipDto clearReview(Long id) {
        ScholarshipEntity s = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with ID: " + id));
        s.setNeedsReview(false);
        s.setReviewReason(null);
        repository.save(s);
        return ScholarshipMapper.toDto(s, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<ScholarshipDto> flagged() {
        LocalDate today = LocalDate.now(clock);
        return repository.findByNeedsReviewTrue().stream().map(s -> ScholarshipMapper.toDto(s, today)).toList();
    }

    @Transactional(readOnly = true)
    public List<DeadlineHistoryEntity> deadlineHistory(Long id) {
        return history.findByScholarshipIdOrderByRecordedAtDesc(id);
    }
}
