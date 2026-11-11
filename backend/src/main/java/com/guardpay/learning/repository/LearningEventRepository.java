package com.guardpay.learning.repository;

import com.guardpay.learning.domain.LearningEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only learning audit events. */
public interface LearningEventRepository extends JpaRepository<LearningEvent, UUID> {

  List<LearningEvent> findTop100ByOrderByOccurredAtDesc();

  List<LearningEvent> findByDatasetIdOrderByOccurredAtDesc(UUID datasetId);

  List<LearningEvent> findByModelIdOrderByOccurredAtDesc(UUID modelId);
}
