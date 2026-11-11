package com.guardpay.feedback;

import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.transaction.Transaction;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records financial payment outcomes after commit.
 *
 * <p>These events are deliberately separate from fraud feedback. A successful
 * capture, refund, reversal, or dispute never creates or changes a fraud label.
 * Recording failures are logged and never alter the payment result.
 */
@Service
public class PaymentOutcomeService {

  private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeService.class);

  private final PaymentOutcomeEventRepository outcomes;

  public PaymentOutcomeService(PaymentOutcomeEventRepository outcomes) {
    this.outcomes = outcomes;
  }

  public void recordAfterCommit(
      Transaction transaction, PaymentOutcomeType type, String source, String eventKey) {
    if (transaction == null || transaction.getId() == null || type == null) {
      return;
    }
    String effectiveSource = source == null || source.isBlank() ? "UNKNOWN" : source;
    String fingerprint =
        RecipientIdentifierHasher.sha256Hex(
            transaction.getId() + "|" + type.name() + "|" + effectiveSource + "|"
                + (eventKey == null ? "" : eventKey));
    Runnable write = () -> write(transaction.getId(), type, effectiveSource, fingerprint);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              write.run();
            }
          });
    } else {
      write.run();
    }
  }

  private void write(
      java.util.UUID transactionId,
      PaymentOutcomeType type,
      String source,
      String fingerprint) {
    try {
      if (outcomes.findByEventFingerprint(fingerprint).isPresent()) {
        return;
      }
      PaymentOutcomeEvent event = new PaymentOutcomeEvent();
      event.setTransactionId(transactionId);
      event.setOutcomeType(type);
      event.setSource(source);
      event.setEventFingerprint(fingerprint);
      event.setFinancialOnly(true);
      event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
      outcomes.save(event);
    } catch (RuntimeException failure) {
      log.warn(
          "Payment outcome recording failed; payment result is unchanged (transaction={}, outcome={})",
          transactionId,
          type,
          failure);
    }
  }
}
