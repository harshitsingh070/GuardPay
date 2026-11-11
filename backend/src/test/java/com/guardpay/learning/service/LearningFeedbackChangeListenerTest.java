package com.guardpay.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.feedback.FraudFeedback;
import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import com.guardpay.learning.domain.LearningDatasetItem;
import com.guardpay.learning.domain.LearningEventType;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.repository.LearningModelRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LearningFeedbackChangeListenerTest {

  @Mock private LearningDatasetItemRepository items;
  @Mock private LearningDatasetRepository datasets;
  @Mock private LearningModelRepository models;
  @Mock private LearningDatasetService datasetService;
  @Mock private LearningAuditService audit;

  private LearningFeedbackChangeListener listener;

  @BeforeEach
  void setUp() {
    listener = new LearningFeedbackChangeListener(items, datasets, models, datasetService, audit);
  }

  @Test
  void correctedVerifiedLabelInvalidatesDatasetAndDerivedModels() {
    UUID feedbackId = UUID.randomUUID();
    UUID datasetId = UUID.randomUUID();
    FraudFeedback feedback = new FraudFeedback();
    ReflectionTestUtils.setField(feedback, "id", feedbackId);
    feedback.setUserReference(UUID.randomUUID());
    feedback.setReviewerReference(UUID.randomUUID());

    LearningDatasetItem item = new LearningDatasetItem();
    item.setDatasetId(datasetId);
    item.setFeedbackId(feedbackId);
    when(items.findByFeedbackId(feedbackId)).thenReturn(List.of(item));

    LearningDataset dataset = new LearningDataset();
    ReflectionTestUtils.setField(dataset, "id", datasetId);
    dataset.setDatasetVersion("dataset-v1-test");
    dataset.setStatus(DatasetStatus.READY);
    when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));

    LearningModel active = new LearningModel();
    ReflectionTestUtils.setField(active, "id", UUID.randomUUID());
    active.setModelVersion("calib-v1-active");
    active.setStatus(ModelStatus.ACTIVE);
    active.setDatasetId(datasetId);
    LearningModel candidate = new LearningModel();
    ReflectionTestUtils.setField(candidate, "id", UUID.randomUUID());
    candidate.setModelVersion("calib-v1-candidate");
    candidate.setStatus(ModelStatus.CANDIDATE);
    candidate.setDatasetId(datasetId);
    when(models.findByDatasetId(datasetId)).thenReturn(List.of(active, candidate));

    listener.onFeedbackChanged(feedback, "Verified label corrected by user");

    verify(datasetService)
        .invalidate(eq(dataset), contains("Verified label corrected by user"), any());
    assertEquals(ModelStatus.INVALIDATED, active.getStatus());
    assertEquals(ModelStatus.INVALIDATED, candidate.getStatus());
    verify(audit, atLeastOnce())
        .record(any(), eq(LearningEventType.MODEL_INVALIDATED), eq(datasetId), any(), any());
  }

  @Test
  void feedbackNeverUsedByADatasetIsIgnored() {
    FraudFeedback feedback = new FraudFeedback();
    ReflectionTestUtils.setField(feedback, "id", UUID.randomUUID());
    when(items.findByFeedbackId(feedback.getId())).thenReturn(List.of());

    listener.onFeedbackChanged(feedback, "label corrected");

    verify(items).findByFeedbackId(feedback.getId());
  }
}
