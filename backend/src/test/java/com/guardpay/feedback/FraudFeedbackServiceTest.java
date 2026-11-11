package com.guardpay.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.User;
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
class FraudFeedbackServiceTest {

  @Mock private FraudFeedbackRepository feedback;
  @Mock private TransactionRepository transactions;
  @Mock private RiskAssessmentRepository assessments;

  private FraudFeedbackService service;
  private User user;
  private Transaction transaction;

  @BeforeEach
  void setUp() {
    service = new FraudFeedbackService(feedback, transactions, assessments);
    user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
  }

  @Test
  void submitSnapshotsTheLatestDecision() {
    RiskAssessment assessment = new RiskAssessment();
    assessment.setRecommendedAction(RecommendedAction.VERIFY);
    assessment.setScore(72);
    when(transactions.findByIdAndUserId(transaction.getId(), user.getId()))
        .thenReturn(Optional.of(transaction));
    when(assessments.findByTransactionOrderByCreatedAtDesc(transaction))
        .thenReturn(List.of(assessment));
    when(feedback.findByTransactionIdAndUserReference(transaction.getId(), user.getId()))
        .thenReturn(Optional.empty());
    when(feedback.save(any(FraudFeedback.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FraudFeedbackDto saved = service.submit(user, transaction.getId(), FeedbackLabel.FRAUD);

    assertEquals("FRAUD", saved.label());
    assertEquals("USER", saved.source());
    assertEquals("VERIFY", saved.decisionAction());
    assertEquals(72, saved.riskScore());
  }

  @Test
  void metricsClassifyFlaggedAndAllowedDecisions() {
    when(feedback.findAllByOrderByCreatedAtDesc())
        .thenReturn(
            List.of(
                row(FeedbackLabel.FRAUD, "VERIFY"),
                row(FeedbackLabel.LEGITIMATE, "STOP"),
                row(FeedbackLabel.LEGITIMATE, "ALLOW"),
                row(FeedbackLabel.FRAUD, "ALLOW"),
                row(FeedbackLabel.FRAUD, null)));

    FeedbackMetricsDto metrics = service.metrics();

    assertEquals(5, metrics.total());
    assertEquals(3, metrics.fraudLabels());
    assertEquals(2, metrics.legitimateLabels());
    assertEquals(1, metrics.truePositive());
    assertEquals(1, metrics.falsePositive());
    assertEquals(1, metrics.trueNegative());
    assertEquals(1, metrics.falseNegative());
    assertEquals(1, metrics.unclassified());
    assertEquals(0.5, metrics.precision());
    assertEquals(0.5, metrics.recall());
  }

  @Test
  void metricsDoNotFabricatePrecisionWithoutLabels() {
    when(feedback.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

    FeedbackMetricsDto metrics = service.metrics();

    assertTrue(metrics.total() == 0);
    assertNull(metrics.precision());
    assertNull(metrics.recall());
  }

  private static FraudFeedback row(FeedbackLabel label, String action) {
    FraudFeedback row = new FraudFeedback();
    ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
    row.setTransactionId(UUID.randomUUID());
    row.setUserReference(UUID.randomUUID());
    row.setLabel(label);
    row.setDecisionAction(action);
    return row;
  }
}
