package com.guardpay.learning.service;

import com.guardpay.feedback.FeedbackChangeListener;
import com.guardpay.feedback.FraudFeedback;
import com.guardpay.learning.domain.LearningDatasetItem;
import com.guardpay.learning.domain.LearningEventType;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.repository.LearningModelRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invalidates every dataset and derived model that used corrected feedback.
 *
 * <p>Best effort by contract: the feedback write must still succeed if learning
 * bookkeeping fails. An invalidated ACTIVE model disappears from the provider,
 * so the deterministic defaults take over immediately.
 */
@Component
public class LearningFeedbackChangeListener implements FeedbackChangeListener {

  private static final Logger log =
      LoggerFactory.getLogger(LearningFeedbackChangeListener.class);

  private final LearningDatasetItemRepository items;
  private final LearningDatasetRepository datasets;
  private final LearningModelRepository models;
  private final LearningDatasetService datasetService;
  private final LearningAuditService audit;

  public LearningFeedbackChangeListener(
      LearningDatasetItemRepository items,
      LearningDatasetRepository datasets,
      LearningModelRepository models,
      LearningDatasetService datasetService,
      LearningAuditService audit) {
    this.items = items;
    this.datasets = datasets;
    this.models = models;
    this.datasetService = datasetService;
    this.audit = audit;
  }

  @Override
  @Transactional
  public void onFeedbackChanged(FraudFeedback feedback, String reason) {
    if (feedback == null || feedback.getId() == null) {
      return;
    }
    List<LearningDatasetItem> used = items.findByFeedbackId(feedback.getId());
    if (used.isEmpty()) {
      return;
    }
    UUID actor =
        feedback.getReviewerReference() != null
            ? feedback.getReviewerReference()
            : feedback.getUserReference();
    if (actor == null) {
      return;
    }
    String detail =
        "Feedback "
            + feedback.getId()
            + " changed: "
            + (reason == null ? "label change" : reason);

    Set<UUID> datasetIds = new LinkedHashSet<>();
    for (LearningDatasetItem item : used) {
      datasetIds.add(item.getDatasetId());
    }
    Set<UUID> invalidatedDatasets = new LinkedHashSet<>();
    for (UUID datasetId : datasetIds) {
      datasets
          .findById(datasetId)
          .ifPresent(
              dataset -> {
                if (dataset.isUsableForTraining()) {
                  datasetService.invalidate(dataset, detail, actor);
                  invalidatedDatasets.add(dataset.getId());
                }
              });
    }

    for (UUID datasetId : invalidatedDatasets) {
      for (LearningModel model : models.findByDatasetId(datasetId)) {
        if (model.getStatus() == ModelStatus.CANDIDATE
            || model.getStatus() == ModelStatus.APPROVED
            || model.getStatus() == ModelStatus.ACTIVE) {
          model.setStatus(ModelStatus.INVALIDATED);
          model.setTrainingFailureReason(LearningAuditService.sanitize(detail));
          model.setRetiredAt(OffsetDateTime.now(ZoneOffset.UTC));
          models.save(model);
          audit.record(
              actor,
              LearningEventType.MODEL_INVALIDATED,
              datasetId,
              model.getId(),
              "Model " + model.getModelVersion() + " invalidated: " + detail);
          log.warn(
              "Learning model {} invalidated after feedback correction", model.getModelVersion());
        }
      }
    }
  }
}
