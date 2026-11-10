package com.guardpay.intelligence.service;

import com.guardpay.transaction.Transaction;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Bridges terminal PayPal dispute/refund events to global intelligence.
 *
 * <p>Captures ids while the caller's transaction is open, then records the
 * evidence after commit in a separate transaction. Never changes the payment
 * result.
 */
@Service
public class RecipientIntelligenceOutcomeService {

  private static final Logger log =
      LoggerFactory.getLogger(RecipientIntelligenceOutcomeService.class);

  private final RecipientIntelligenceOutcomeWriter writer;

  public RecipientIntelligenceOutcomeService(RecipientIntelligenceOutcomeWriter writer) {
    this.writer = writer;
  }

  public void recordExclusionAfterCommit(Transaction transaction, String source) {
    if (transaction == null || transaction.getId() == null) {
      return;
    }
    if (transaction.getUser() == null || transaction.getRecipient() == null) {
      return;
    }
    UUID userId = transaction.getUser().getId();
    UUID recipientId = transaction.getRecipient().getId();
    UUID transactionId = transaction.getId();
    String effectiveSource = source == null || source.isBlank() ? "UNKNOWN" : source;

    Runnable work =
        () -> {
          try {
            writer.record(userId, recipientId, transactionId, effectiveSource);
          } catch (RuntimeException failure) {
            log.warn(
                "Recipient intelligence outcome evidence failed; payment result is unchanged "
                    + "(transaction={})",
                transactionId,
                failure);
          }
        };

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              work.run();
            }
          });
    } else {
      work.run();
    }
  }
}
