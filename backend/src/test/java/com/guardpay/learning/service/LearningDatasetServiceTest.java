package com.guardpay.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.guardpay.feedback.FeedbackLabel;
import com.guardpay.feedback.FeedbackStatus;
import com.guardpay.feedback.FraudFeedback;
import com.guardpay.feedback.FraudFeedbackRepository;
import com.guardpay.learning.domain.DatasetStatus;
import com.guardpay.learning.domain.LearningDataset;
import com.guardpay.learning.repository.LearningDatasetExclusionRepository;
import com.guardpay.learning.repository.LearningDatasetItemRepository;
import com.guardpay.learning.repository.LearningDatasetRepository;
import com.guardpay.learning.web.dto.LearningDtos.DatasetDto;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LearningDatasetServiceTest {

  @Mock private FraudFeedbackRepository feedback;
  @Mock private RiskAssessmentRepository assessments;
  @Mock private LearningDatasetRepository datasets;
  @Mock private LearningDatasetItemRepository items;
  @Mock private LearningDatasetExclusionRepository exclusions;
  @Mock private LearningAuditService audit;

  private LearningDatasetService service;

  @BeforeEach
  void setUp() {
    service = new LearningDatasetService(feedback, assessments, datasets, items, exclusions, audit);
  }

  @Test
  void buildsChronologicalReadyDatasetFromVerifiedFeedback() {
    Map<UUID, RiskAssessment> byId = new HashMap<>();
    List<FraudFeedback> rows = new ArrayList<>();
    OffsetDateTime start = OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    for (int i = 0; i < 20; i++) {
      rows.add(feedbackRow(byId, i, i % 2 == 0 ? FeedbackLabel.FRAUD : FeedbackLabel.LEGITIMATE,
          start.plusMinutes(i)));
    }
    when(feedback.findByFeedbackStatusInOrderByCreatedAtAsc(any())).thenReturn(rows);
    when(assessments.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(byId.get(invocation.getArgument(0))));
    when(datasets.findByChecksum(any())).thenReturn(Optional.empty());
    when(datasets.save(any(LearningDataset.class)))
        .thenAnswer(
            invocation -> {
              LearningDataset dataset = invocation.getArgument(0);
              ReflectionTestUtils.setField(dataset, "id", UUID.randomUUID());
              return dataset;
            });

    DatasetDto result = service.build(UUID.randomUUID());

    assertEquals("READY", result.status());
    assertEquals(20, result.recordCount());
    assertEquals(10, result.fraudCount());
    assertEquals(10, result.legitimateCount());
    assertEquals(14, result.trainCount());
    assertEquals(3, result.validationCount());
    assertEquals(3, result.testCount());
    assertEquals(0, result.excludedCount());
    assertTrue(result.unmetRequirements().isEmpty());
    assertTrue(result.datasetVersion().startsWith("dataset-v1-"));
    assertEquals(64, result.checksum().length());
  }

  @Test
  void insufficientVerifiedLabelsProduceFirstClassInsufficientDataset() {
    Map<UUID, RiskAssessment> byId = new HashMap<>();
    List<FraudFeedback> rows =
        List.of(
            feedbackRow(byId, 0, FeedbackLabel.FRAUD, OffsetDateTime.now(ZoneOffset.UTC)),
            feedbackRow(byId, 1, FeedbackLabel.FRAUD, OffsetDateTime.now(ZoneOffset.UTC)),
            feedbackRow(byId, 2, FeedbackLabel.LEGITIMATE, OffsetDateTime.now(ZoneOffset.UTC)));
    when(feedback.findByFeedbackStatusInOrderByCreatedAtAsc(any())).thenReturn(rows);
    when(assessments.findById(any()))
        .thenAnswer(invocation -> Optional.ofNullable(byId.get(invocation.getArgument(0))));
    when(datasets.findByChecksum(any())).thenReturn(Optional.empty());
    when(datasets.save(any(LearningDataset.class)))
        .thenAnswer(
            invocation -> {
              LearningDataset dataset = invocation.getArgument(0);
              ReflectionTestUtils.setField(dataset, "id", UUID.randomUUID());
              return dataset;
            });

    DatasetDto result = service.build(UUID.randomUUID());

    assertEquals(DatasetStatus.INSUFFICIENT_DATA.name(), result.status());
    assertEquals(3, result.recordCount());
    assertEquals(3, result.unmetRequirements().size());
    assertTrue(
        result.unmetRequirements().stream().anyMatch(reason -> reason.contains("verified records")));
  }

  @Test
  void missingAssessmentIsRecordedAsExclusionInsteadOfInventingFeatures() {
    FraudFeedback row = new FraudFeedback();
    UUID feedbackId = UUID.randomUUID();
    ReflectionTestUtils.setField(row, "id", feedbackId);
    row.setTransactionId(UUID.randomUUID());
    row.setLabel(FeedbackLabel.FRAUD);
    row.setFeedbackStatus(FeedbackStatus.VERIFIED_FRAUD);
    when(feedback.findByFeedbackStatusInOrderByCreatedAtAsc(any())).thenReturn(List.of(row));
    when(assessments.findByTransactionIdInOrderByCreatedAtDesc(any())).thenReturn(List.of());
    when(datasets.findByChecksum(any())).thenReturn(Optional.empty());
    when(datasets.save(any(LearningDataset.class)))
        .thenAnswer(
            invocation -> {
              LearningDataset dataset = invocation.getArgument(0);
              ReflectionTestUtils.setField(dataset, "id", UUID.randomUUID());
              return dataset;
            });

    DatasetDto result = service.build(UUID.randomUUID());

    assertEquals(0, result.recordCount());
    assertEquals(1, result.excludedCount());
    assertEquals(DatasetStatus.INSUFFICIENT_DATA.name(), result.status());
  }

  private static FraudFeedback feedbackRow(
      Map<UUID, RiskAssessment> byId, int index, FeedbackLabel label, OffsetDateTime at) {
    FraudFeedback row = new FraudFeedback();
    UUID feedbackId = UUID.randomUUID();
    ReflectionTestUtils.setField(row, "id", feedbackId);
    row.setTransactionId(UUID.randomUUID());
    row.setLabel(label);
    row.setFeedbackStatus(
        label == FeedbackLabel.FRAUD
            ? FeedbackStatus.VERIFIED_FRAUD
            : FeedbackStatus.VERIFIED_LEGITIMATE);

    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal("100").add(new BigDecimal(index)));
    transaction.setCurrency("USD");
    transaction.setTransactionTimestamp(at);

    RiskAssessment assessment = new RiskAssessment();
    UUID assessmentId = UUID.randomUUID();
    ReflectionTestUtils.setField(assessment, "id", assessmentId);
    assessment.setTransaction(transaction);
    assessment.setScore(label == FeedbackLabel.FRAUD ? 90 : 10);
    assessment.setRecommendedAction(
        label == FeedbackLabel.FRAUD ? RecommendedAction.STOP : RecommendedAction.ALLOW);
    assessment.setFindings(
        label == FeedbackLabel.FRAUD
            ? "[{\"code\":\"NEW_RECIPIENT\"}]"
            : "[{\"code\":\"SAFE\"}]");
    row.setRiskAssessmentId(assessmentId);
    byId.put(assessmentId, assessment);
    return row;
  }
}
