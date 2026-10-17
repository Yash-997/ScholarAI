package com.scholarship.service;

import com.scholarship.dto.ScholarshipDto;
import com.scholarship.entity.ScholarshipEntity;
import com.scholarship.exception.ResourceNotFoundException;
import com.scholarship.repository.ScholarshipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ScholarshipService {

    private final ScholarshipRepository scholarshipRepository;
    private final ScholarshipIngestionService scholarshipIngestionService;
    private final Clock clock;

    public ScholarshipService(ScholarshipRepository scholarshipRepository, ScholarshipIngestionService scholarshipIngestionService, Clock clock) {
        this.scholarshipRepository = scholarshipRepository;
        this.scholarshipIngestionService = scholarshipIngestionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ScholarshipDto> getScholarships(String state, String course, String provider, String schemeType, Pageable pageable) {
        return getScholarships(state, course, provider, schemeType, true, null, pageable);
    }

    /**
     * @param includeClosed false hides schemes whose verified window has closed (they remain available with true)
     * @param status        optional exact status filter (OPEN, UPCOMING, CLOSED, UNKNOWN, UNVERIFIED)
     */
    @Transactional(readOnly = true)
    public Page<ScholarshipDto> getScholarships(String state, String course, String provider, String schemeType,
                                                boolean includeClosed, String status, Pageable pageable) {
        if (includeClosed && (status == null || status.isBlank())) {
            return scholarshipRepository.findWithFilters(state, course, provider, schemeType, pageable).map(this::toDto);
        }
        // Status is computed from dates and today's date, so it cannot be filtered in SQL: filter, then page, in memory.
        List<ScholarshipDto> all = scholarshipRepository
                .findWithFilters(state, course, provider, schemeType, PageRequest.of(0, 1000, pageable.getSort()))
                .map(this::toDto).getContent().stream()
                .filter(d -> includeClosed || !"CLOSED".equals(d.status()))
                .filter(d -> status == null || status.isBlank() || status.equalsIgnoreCase(d.status()))
                .toList();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    /** Programmes found automatically on partner pages and awaiting a reviewer. Their criteria are not applied to eligibility. */
    @Transactional(readOnly = true)
    public List<ScholarshipDto> getDiscovered() {
        return scholarshipRepository.findByVerificationStatusAndExternalKeyIsNotNull("PENDING_REVIEW").stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ScholarshipDto getScholarshipById(Long id) {
        ScholarshipEntity entity = scholarshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with ID: " + id));
        return toDto(entity);
    }

    @Transactional
    public ScholarshipDto createScholarship(ScholarshipDto dto) {
        ScholarshipEntity entity = new ScholarshipEntity();
        mapDtoToEntity(dto, entity);
        ScholarshipEntity saved = scholarshipRepository.save(entity);

        scholarshipIngestionService.indexSingleScholarship(saved);
        return toDto(saved);
    }

    @Transactional
    public ScholarshipDto updateScholarship(Long id, ScholarshipDto dto) {
        ScholarshipEntity entity = scholarshipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scholarship not found with ID: " + id));

        mapDtoToEntity(dto, entity);
        ScholarshipEntity saved = scholarshipRepository.save(entity);

        scholarshipIngestionService.indexSingleScholarship(saved);
        return toDto(saved);
    }

    @Transactional
    public void deleteScholarship(Long id) {
        if (!scholarshipRepository.existsById(id)) {
            throw new ResourceNotFoundException("Scholarship not found with ID: " + id);
        }
        scholarshipRepository.deleteById(id);
        scholarshipIngestionService.removeFromIndex(id);
    }

    public ScholarshipDto toDto(ScholarshipEntity entity) {
        return ScholarshipMapper.toDto(entity, LocalDate.now(clock));
    }

    private void mapDtoToEntity(ScholarshipDto dto, ScholarshipEntity entity) {
        entity.setName(dto.name());
        entity.setProvider(dto.provider());
        entity.setSchemeType(dto.schemeType());
        entity.setEligibility(dto.eligibility());
        entity.setIncomeLimit(dto.incomeLimit());
        entity.setMaximumFamilyIncome(dto.maximumFamilyIncome() != null ? dto.maximumFamilyIncome() : dto.incomeLimit());
        entity.setMinimumMarksOrCgpa(dto.minimumMarksOrCgpa());
        entity.setCourse(dto.course());
        entity.setState(dto.state());
        entity.setCategoryCriteria(dto.categoryCriteria());
        entity.setGenderRequirements(dto.genderRequirements());
        entity.setBenefits(dto.benefits());
        if (!java.util.Objects.equals(entity.getDeadline(), dto.deadline()) || !java.util.Objects.equals(entity.getOpenDate(), dto.openDate())) {
            // Changed through the generic CRUD API: keep the value but drop "verified" until a reviewer confirms it.
            entity.setDeadlineVerifiedOn(null);
        }
        entity.setDeadline(dto.deadline());
        entity.setOpenDate(dto.openDate());
        if (dto.academicYear() != null) entity.setAcademicYear(dto.academicYear());
        entity.setRequiredDocuments(dto.requiredDocuments());
        entity.setApplicationUrl(dto.applicationUrl());
        entity.setSourceUrl(dto.sourceUrl());
        entity.setLastVerified(dto.lastVerified());
        if (dto.verificationStatus() != null) {
            entity.setVerificationStatus(dto.verificationStatus());
        }
    }
}
