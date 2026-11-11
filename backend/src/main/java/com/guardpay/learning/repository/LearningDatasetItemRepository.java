package com.guardpay.learning.repository;

import com.guardpay.learning.domain.LearningDatasetItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for dataset feature snapshots. */
public interface LearningDatasetItemRepository extends JpaRepository<LearningDatasetItem, UUID> {

  List<LearningDatasetItem> findByDatasetIdOrderByOccurredAtAsc(UUID datasetId);

  List<LearningDatasetItem> findByDatasetIdAndSplitNameOrderByOccurredAtAsc(
      UUID datasetId, String splitName);

  List<LearningDatasetItem> findByFeedbackId(UUID feedbackId);

  long countByDatasetId(UUID datasetId);
}
