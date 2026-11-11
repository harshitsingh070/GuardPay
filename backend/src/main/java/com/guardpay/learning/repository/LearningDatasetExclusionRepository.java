package com.guardpay.learning.repository;

import com.guardpay.learning.domain.LearningDatasetExclusion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for explicit dataset exclusions. */
public interface LearningDatasetExclusionRepository
    extends JpaRepository<LearningDatasetExclusion, UUID> {

  List<LearningDatasetExclusion> findByDatasetIdOrderByCreatedAtAsc(UUID datasetId);
}
