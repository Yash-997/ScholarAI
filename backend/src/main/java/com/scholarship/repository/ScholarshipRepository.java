package com.scholarship.repository;

import com.scholarship.entity.ScholarshipEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ScholarshipRepository extends JpaRepository<ScholarshipEntity, Long> {

    @Query("""
        SELECT s FROM ScholarshipEntity s
        WHERE s.verificationStatus = 'VERIFIED'
          AND (CAST(:state AS string) IS NULL OR LOWER(s.state) LIKE LOWER(CONCAT('%', CAST(:state AS string), '%')) OR LOWER(s.state) IN ('all', 'all india'))
          AND (CAST(:course AS string) IS NULL OR LOWER(s.course) LIKE LOWER(CONCAT('%', CAST(:course AS string), '%')) OR LOWER(s.course) IN ('all', 'all courses'))
          AND (CAST(:provider AS string) IS NULL OR LOWER(s.provider) LIKE LOWER(CONCAT('%', CAST(:provider AS string), '%')))
          AND (CAST(:schemeType AS string) IS NULL OR LOWER(s.schemeType) LIKE LOWER(CONCAT('%', CAST(:schemeType AS string), '%')))
    """)
    Page<ScholarshipEntity> findWithFilters(
            @Param("state") String state,
            @Param("course") String course,
            @Param("provider") String provider,
            @Param("schemeType") String schemeType,
            Pageable pageable
    );

    List<ScholarshipEntity> findByIdIn(Collection<Long> ids);

    List<ScholarshipEntity> findByVerificationStatus(String verificationStatus);

    List<ScholarshipEntity> findByDedupeKey(String dedupeKey);

    List<ScholarshipEntity> findBySourceId(String sourceId);

    List<ScholarshipEntity> findByNeedsReviewTrue();

    List<ScholarshipEntity> findByExternalKey(String externalKey);

    List<ScholarshipEntity> findByVerificationStatusAndExternalKeyIsNotNull(String verificationStatus);
}
