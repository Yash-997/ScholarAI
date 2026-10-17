package com.scholarship.repository;

import com.scholarship.entity.DeadlineHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DeadlineHistoryRepository extends JpaRepository<DeadlineHistoryEntity, Long> {

    List<DeadlineHistoryEntity> findByScholarshipIdOrderByRecordedAtDesc(Long scholarshipId);

    boolean existsByScholarshipIdAndDeadlineAndState(Long scholarshipId, LocalDate deadline, String state);

    void deleteByScholarshipId(Long scholarshipId);
}
