package com.guardpay.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.guardpay.feedback.FeedbackLabel;
import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import com.guardpay.learning.domain.LearningDatasetItem;
import com.guardpay.learning.domain.LearningModel;
import com.guardpay.learning.domain.ModelStatus;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.repository.LearningEventRepository;
import com.guardpay.learning.repository.LearningModelRepository;
import com.guardpay.learning.web.dto.LearningDtos.ModelDto;
import com.guardpay.learning.web.dto.LearningDtos.TrainingResultDto;
import com.guardpay.risk.RiskCalibration;
import com.guardpay.risk.RiskCalibrationProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
class LearningModelServiceTest {

  @Mock private LearningModelRepository models;
  @Mock private LearningDatasetRepository datasets;
  @Mock private LearningDatasetItemRepository items;
  @Mock private LearningEventRepository events;
  @Mock private LearningDatasetService datasetService;
  @Mock private LearningAuditService audit;
  @Mock private RiskCalibrationProvider calibrationProvider;

  private LearningModelService service;

  @BeforeEach
  void setUp() {
    service =
        new LearningModelService(
            models, datasets, items, events, datasetService, audit, calibrationProvider);
  }

  @Test
  void trainsInspectableCandidateFromSeparatedScores() {
    LearningDataset dataset = readyDataset(20, 10, 10);
    when(datasetService.require(dataset.getId())).thenReturn(dataset);
    when(items.findByDatasetIdOrderByOccurredAtAsc(dataset.getId()))
        .thenReturn(rows(7, 7, 3, 3));
    when(models.findByDatasetId(dataset.getId())).thenReturn(List.of());
    when(models.findByModelVersion(any())).thenReturn(Optional.empty());

    TrainingResultDto result = service.train(dataset.getId(), UUID.randomUUID());

    assertTrue(result.trained());
    assertEquals("CANDIDATE", result.status());
    assertEquals(89, result.verifyThreshold());
    assertEquals(90, result.stopThreshold());
    assertEquals(1.0, result.precision());
    assertEquals(1.0, result.recall());
    assertEquals(1.0, result.f1());
    assertEquals(2, result.validationCount());
    assertEquals(6, result.testCount());
    assertNotNull(result.championComparisonJson());
  }

  @Test
  void refusesTrainingWithoutMinimumVerifiedData() {
    LearningDataset dataset = readyDataset(3, 2, 1);
    when(datasetService.require(dataset.getId())).thenReturn(dataset);
    when(models.findByDatasetId(dataset.getId())).thenReturn(List.of());
    when(models.findByModelVersion(any())).thenReturn(Optional.empty());

    TrainingResultDto result = service.train(dataset.getId(), UUID.randomUUID());

    assertFalse(result.trained());
    assertEquals("REJECTED", result.status());
    assertTrue(result.failureReason().contains("verified records"));
    assertTrue(result.failureReason().contains("verified fraud"));
    assertTrue(result.failureReason().contains("verified legitimate"));
  }

  @Test
  void deployRetiresChampionAndRollbackRestoresIt() {
    UUID datasetId = UUID.randomUUID();
    LearningDataset dataset = readyDataset(20, 10, 10);
    ReflectionTestUtils.setField(dataset, "id", datasetId);

    LearningModel previous = new LearningModel();
    UUID previousId = UUID.randomUUID();
    ReflectionTestUtils.setField(previous, "id", previousId);
    previous.setModelVersion("calib-v1-old");
    previous.setStatus(ModelStatus.ACTIVE);
    previous.setDatasetId(datasetId);
    previous.setArtifactJson("{\"verifyThreshold\":40,\"stopThreshold\":85}");

    LearningModel candidate = new LearningModel();
    UUID candidateId = UUID.randomUUID();
    ReflectionTestUtils.setField(candidate, "id", candidateId);
    candidate.setModelVersion("calib-v1-new");
    candidate.setStatus(ModelStatus.APPROVED);
    candidate.setDatasetId(datasetId);
    candidate.setArtifactJson("{\"verifyThreshold\":30,\"stopThreshold\":80}");

    when(models.findByIdForUpdate(candidateId)).thenReturn(Optional.of(candidate));
    when(datasets.findById(datasetId)).thenReturn(Optional.of(dataset));
    when(models.findActiveByType(LearningRiskCalibrationProvider.MODEL_TYPE))
        .thenReturn(List.of(previous));

    ModelDto deployed = service.deploy(candidateId, UUID.randomUUID());

    assertEquals("ACTIVE", deployed.status());
    assertEquals(previousId.toString(), deployed.rollbackReference());
    assertEquals(ModelStatus.RETIRED, previous.getStatus());

    when(models.findByIdForUpdate(candidateId)).thenReturn(Optional.of(candidate));
    when(models.findById(previousId)).thenReturn(Optional.of(previous));
    ReflectionTestUtils.setField(candidate, "rollbackReference", previousId);

    ModelDto restored = service.rollback(candidateId, UUID.randomUUID());

    assertEquals("calib-v1-old", restored.modelVersion());
    assertEquals("ACTIVE", restored.status());
    assertEquals(ModelStatus.ROLLED_BACK, candidate.getStatus());
  }

  @Test
  void overviewReportsDefaultsWhenNothingDeployed() {
    when(calibrationProvider.activeCalibration()).thenReturn(RiskCalibration.defaults());
    when(models.findFirstByStatusAndModelTypeOrderByActivatedAtDesc(
            eq(ModelStatus.ACTIVE), eq(LearningRiskCalibrationProvider.MODEL_TYPE)))
        .thenReturn(Optional.empty());
    when(datasets.count()).thenReturn(4L);
    when(datasets.countByStatus(DatasetStatus.READY)).thenReturn(3L);
    when(datasets.countByStatus(DatasetStatus.INSUFFICIENT_DATA)).thenReturn(1L);
    when(datasets.countByStatus(DatasetStatus.INVALIDATED)).thenReturn(0L);
    when(models.count()).thenReturn(2L);
    when(models.countByStatus(ModelStatus.CANDIDATE)).thenReturn(1L);
    when(models.countByStatus(ModelStatus.ACTIVE)).thenReturn(0L);
    when(events.findTop100ByOrderByOccurredAtDesc()).thenReturn(List.of());

    var overview = service.overview();

    assertFalse(overview.activeCalibration().deployed());
    assertEquals("deterministic-default", overview.activeCalibration().modelVersion());
    assertEquals(40, overview.activeCalibration().verifyThreshold());
    assertEquals(85, overview.activeCalibration().stopThreshold());
    assertEquals(4, overview.totalDatasets());
  }

  private static LearningDataset readyDataset(int total, int fraud, int legitimate) {
    LearningDataset dataset = new LearningDataset();
    ReflectionTestUtils.setField(dataset, "id", UUID.randomUUID());
    dataset.setDatasetVersion("dataset-v1-test");
    dataset.setStatus(DatasetStatus.READY);
    dataset.setFeatureVersion("risk-assessment-v1");
    dataset.setLabelPolicyVersion("verified-labels-v1");
    dataset.setChecksum("a".repeat(64));
    dataset.setRecordCount(total);
    dataset.setFraudCount(fraud);
    dataset.setLegitimateCount(legitimate);
    dataset.setTrainCount(Math.max(1, total - 6));
    dataset.setValidationCount(3);
    dataset.setTestCount(3);
    dataset.setBuiltAt(OffsetDateTime.now(ZoneOffset.UTC));
    dataset.setCreatedBy(UUID.randomUUID());
    return dataset;
  }

  private static List<LearningDatasetItem> rows(
      int trainFraud, int trainLegit, int testFraud, int testLegit) {
    List<LearningDatasetItem> rows = new ArrayList<>();
    for (int i = 0; i < trainFraud; i++) {
      rows.add(row(90, FeedbackLabel.FRAUD, "TRAIN"));
    }
    for (int i = 0; i < trainLegit; i++) {
      rows.add(row(10, FeedbackLabel.LEGITIMATE, "TRAIN"));
    }
    for (int i = 0; i < testFraud; i++) {
      rows.add(row(90, FeedbackLabel.FRAUD, "TEST"));
    }
    for (int i = 0; i < testLegit; i++) {
      rows.add(row(10, FeedbackLabel.LEGITIMATE, "TEST"));
    }
    rows.add(row(90, FeedbackLabel.FRAUD, "VALIDATION"));
    rows.add(row(10, FeedbackLabel.LEGITIMATE, "VALIDATION"));
    return rows;
  }

  private static LearningDatasetItem row(int score, FeedbackLabel label, String split) {
    LearningDatasetItem item = new LearningDatasetItem();
    item.setRiskScore(score);
    item.setLabel(label.name());
    item.setSplitName(split);
    item.setFeatureJson("{}");
    item.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
    return item;
  }
}
