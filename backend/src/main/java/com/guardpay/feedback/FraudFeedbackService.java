package com.guardpay.feedback;

import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Captures explicit fraud/legitimate labels and turns them into evaluation
 * metrics. The labels are the feedback loop: they can be exported for offline
 * retraining and threshold tuning, but never change a live decision.
 */
@Service
public class FraudFeedbackService {

  private final FraudFeedbackRepository feedback;
  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;

  public FraudFeedbackService(
      FraudFeedbackRepository feedback,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments) {
    this.feedback = feedback;
    this.transactions = transactions;
    this.assessments = assessments;
  }

  @Transactional
  public FraudFeedbackDto submit(User user, UUID transactionId, FeedbackLabel label) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (transactionId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction is required");
    }
    if (label == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Label is required");
    }
    Transaction transaction =
        transactions
            .findByIdAndUserId(transactionId, user.getId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));

    FraudFeedback row =
        feedback
            .findByTransactionId(transactionId)
            .orElseGet(FraudFeedback::new);
    if (row.getId() != null && !user.getId().equals(row.getUserReference())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Feedback belongs to another user");
    }
    RiskAssessment latest =
        assessments.findByTransactionOrderByCreatedAtDesc(transaction).stream()
            .findFirst()
            .orElse(null);

    row.setTransactionId(transactionId);
    row.setUserReference(user.getId());
    row.setLabel(label);
    row.setSource(user.isAdmin() ? FeedbackSource.ADMIN : FeedbackSource.USER);
    row.setDecisionAction(
        latest != null && latest.getRecommendedAction() != null
            ? latest.getRecommendedAction().name()
            : null);
    row.setRiskScore(latest != null ? latest.getScore() : null);
    return toDto(feedback.save(row));
  }

  @Transactional(readOnly = true)
  public Optional<FraudFeedbackDto> forTransaction(User user, UUID transactionId) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    transactions
        .findByIdAndUserId(transactionId, user.getId())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));
    return feedback
        .findByTransactionIdAndUserReference(transactionId, user.getId())
        .map(FraudFeedbackService::toDto);
  }

  @Transactional(readOnly = true)
  public FeedbackMetricsDto metrics() {
    return metrics(feedback.findAllByOrderByCreatedAtDesc());
  }

  @Transactional(readOnly = true)
  public List<FraudFeedbackDto> export() {
    return feedback.findAllByOrderByCreatedAtDesc().stream()
        .map(FraudFeedbackService::toDto)
        .toList();
  }

  private static FeedbackMetricsDto metrics(List<FraudFeedback> rows) {
    long truePositive = 0;
    long falsePositive = 0;
    long trueNegative = 0;
    long falseNegative = 0;
    long unclassified = 0;
    long fraudLabels = 0;
    long legitimateLabels = 0;

    for (FraudFeedback row : rows) {
      if (row.getLabel() == FeedbackLabel.FRAUD) {
        fraudLabels++;
      } else if (row.getLabel() == FeedbackLabel.LEGITIMATE) {
        legitimateLabels++;
      }
      String action = row.getDecisionAction();
      if (action == null) {
        unclassified++;
        continue;
      }
      boolean flagged = "VERIFY".equals(action) || "STOP".equals(action);
      if (row.getLabel() == FeedbackLabel.FRAUD) {
        if (flagged) {
          truePositive++;
        } else {
          falseNegative++;
        }
      } else if (row.getLabel() == FeedbackLabel.LEGITIMATE) {
        if (flagged) {
          falsePositive++;
        } else {
          trueNegative++;
        }
      }
    }

    Double precision =
        truePositive + falsePositive == 0
            ? null
            : (double) truePositive / (truePositive + falsePositive);
    Double recall =
        truePositive + falseNegative == 0
            ? null
            : (double) truePositive / (truePositive + falseNegative);
    return new FeedbackMetricsDto(
        rows.size(),
        fraudLabels,
        legitimateLabels,
        truePositive,
        falsePositive,
        trueNegative,
        falseNegative,
        unclassified,
        precision,
        recall);
  }

  private static FraudFeedbackDto toDto(FraudFeedback row) {
    return new FraudFeedbackDto(
        row.getId(),
        row.getTransactionId(),
        row.getLabel() == null ? null : row.getLabel().name(),
        row.getSource() == null ? null : row.getSource().name(),
        row.getDecisionAction(),
        row.getRiskScore(),
        row.getCreatedAt());
  }
}
