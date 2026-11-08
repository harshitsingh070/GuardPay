package com.guardpay.behavior;

import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bridges terminal payment outcomes to behavioral learning.
 *
 * <p>The risk decision and verification state are resolved while the caller's
 * transaction is still open. Learning is scheduled after commit so the learner
 * sees the committed terminal state, and it runs in its own
 * {@code REQUIRES_NEW} transaction inside {@link BehavioralProfileService}.
 * Failures are logged and never change the payment result.
 */
@Service
public class BehavioralOutcomeService {

  private static final Logger log = LoggerFactory.getLogger(BehavioralOutcomeService.class);

  private final BehavioralProfileService profiles;
  private final RiskAssessmentRepository assessments;
  private final PaymentVerificationRepository verifications;

  public BehavioralOutcomeService(
      BehavioralProfileService profiles,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications) {
    this.profiles = profiles;
    this.assessments = assessments;
    this.verifications = verifications;
  }

  /** Records the outcome implied by the transaction's current terminal status. */
  public void recordAfterCommit(Transaction transaction, String source) {
    if (transaction == null || transaction.getId() == null) {
      return;
    }
    BehavioralOutcome outcome = outcomeFor(transaction.getStatus());
    if (outcome == null) {
      return;
    }
    RecommendedAction action = resolveAction(transaction);
    boolean verified = action == RecommendedAction.VERIFY && isVerified(transaction);
    schedule(transaction, outcome, action, verified, source);
  }

  /** Records a non-learnable exclusion such as a refund, reversal, or dispute. */
  public void recordExclusionAfterCommit(
      Transaction transaction, BehavioralOutcome outcome, String source) {
    if (transaction == null || transaction.getId() == null || outcome == null) {
      return;
    }
    schedule(transaction, outcome, resolveAction(transaction), false, source);
  }

  private void schedule(
      Transaction transaction,
      BehavioralOutcome outcome,
      RecommendedAction action,
      boolean explicitlyVerified,
      String source) {
    UUID userId =
        transaction.getUser() != null ? transaction.getUser().getId() : null;
    if (userId == null) {
      log.debug("Behavioral outcome skipped: transaction has no owner");
      return;
    }
    UUID transactionId = transaction.getId();
    TransactionStatus status = transaction.getStatus();
    OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
    String effectiveSource = source == null || source.isBlank() ? "UNKNOWN" : source;

    Runnable learning =
        () -> {
          try {
            profiles.updateProfileAfterOutcome(
                userId,
                transaction,
                asOf,
                action,
                outcome,
                explicitlyVerified,
                effectiveSource);
          } catch (RuntimeException failure) {
            log.warn(
                "Behavioral outcome learning failed; payment result is unchanged "
                    + "(transaction={}, outcome={}, status={})",
                transactionId,
                outcome,
                status,
                failure);
          }
        };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              learning.run();
            }
          });
    } else {
      learning.run();
    }
  }

  private RecommendedAction resolveAction(Transaction transaction) {
    return assessments
        .findByTransactionOrderByCreatedAtDesc(transaction)
        .stream()
        .findFirst()
        .map(assessment -> assessment.getRecommendedAction())
        .orElse(null);
  }

  private boolean isVerified(Transaction transaction) {
    return verifications
        .findByTransactionId(transaction.getId())
        .map(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
        .orElse(false);
  }

  private static BehavioralOutcome outcomeFor(TransactionStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case COMPLETED -> BehavioralOutcome.COMPLETED;
      case FAILED -> BehavioralOutcome.FAILED;
      case CANCELLED -> BehavioralOutcome.CANCELLED;
      default -> null;
    };
  }
}
