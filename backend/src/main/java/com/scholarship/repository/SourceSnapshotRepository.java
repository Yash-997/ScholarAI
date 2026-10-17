package com.scholarship.repository;

import com.scholarship.entity.SourceSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceSnapshotRepository extends JpaRepository<SourceSnapshotEntity, String> {
}
