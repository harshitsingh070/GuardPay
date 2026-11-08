package com.guardpay.behavior;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Immutable ledger of behavioral learning outcomes.
 *
 * <p>One row per {@code (transaction, outcome)}. The unique constraint is the
 * idempotency boundary: duplicate and concurrent delivery of the same outcome
 * cannot insert twice. Rows with {@code learnable = false} exclude the
 * transaction from future baseline recomputation, including refunds, reversals,
 * disputes, failed payments, and suspicious completed payments.
 */
@Entity
@Table(
    name = "behavioral_outcome_events",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_behavioral_outcome_event",
          columnNames = {"transaction_id", "outcome"})
    })
public class BehavioralOutcomeEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "transaction_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_behavioral_outcome_events_transaction"))
  private Transaction transaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", length = 24, nullable = false)
  private BehavioralOutcome outcome;

  @Column(name = "learnable", nullable = false)
  private boolean learnable;

  @Column(name = "source", length = 48, nullable = false)
  private String source;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommended_action", length = 16)
  private RecommendedAction recommendedAction;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC);

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public BehavioralOutcome getOutcome() {
    return outcome;
  }

  public void setOutcome(BehavioralOutcome outcome) {
    this.outcome = outcome;
  }

  public boolean isLearnable() {
    return learnable;
  }

  public void setLearnable(boolean learnable) {
    this.learnable = learnable;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public RecommendedAction getRecommendedAction() {
    return recommendedAction;
  }

  public void setRecommendedAction(RecommendedAction recommendedAction) {
    this.recommendedAction = recommendedAction;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
