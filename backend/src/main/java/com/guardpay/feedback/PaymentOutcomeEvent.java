package com.guardpay.feedback;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Financial lifecycle event, kept separate from fraud labels. */
@Entity
@Table(
    name = "payment_outcome_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_payment_outcome_fingerprint",
            columnNames = "event_fingerprint"))
public class PaymentOutcomeEvent extends BaseEntity {

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome_type", length = 24, nullable = false)
  private PaymentOutcomeType outcomeType;

  @Column(name = "source", length = 64, nullable = false)
  private String source;

  @Column(name = "event_fingerprint", length = 64, nullable = false)
  private String eventFingerprint;

  @Column(name = "financial_only", nullable = false)
  private boolean financialOnly = true;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  public UUID getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(UUID transactionId) {
    this.transactionId = transactionId;
  }

  public PaymentOutcomeType getOutcomeType() {
    return outcomeType;
  }

  public void setOutcomeType(PaymentOutcomeType outcomeType) {
    this.outcomeType = outcomeType;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getEventFingerprint() {
    return eventFingerprint;
  }

  public void setEventFingerprint(String eventFingerprint) {
    this.eventFingerprint = eventFingerprint;
  }

  public boolean isFinancialOnly() {
    return financialOnly;
  }

  public void setFinancialOnly(boolean financialOnly) {
    this.financialOnly = financialOnly;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
