package com.guardpay.feedback;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * One ground-truth label for a transaction decision.
 *
 * <p>The decision snapshot is stored with the label so precision/recall can be
 * computed later even if the transaction is re-assessed. Labels are explicit
 * user/admin feedback, never inferred from payment status.
 */
@Entity
@Table(
    name = "fraud_feedback",
    uniqueConstraints = @UniqueConstraint(name = "uq_fraud_feedback_transaction", columnNames = "transaction_id"))
public class FraudFeedback extends BaseEntity {

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "user_reference", nullable = false)
  private UUID userReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "label", length = 16, nullable = false)
  private FeedbackLabel label;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 16, nullable = false)
  private FeedbackSource source = FeedbackSource.USER;

  @Column(name = "decision_action", length = 16)
  private String decisionAction;

  @Column(name = "risk_score")
  private Integer riskScore;

  public UUID getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(UUID transactionId) {
    this.transactionId = transactionId;
  }

  public UUID getUserReference() {
    return userReference;
  }

  public void setUserReference(UUID userReference) {
    this.userReference = userReference;
  }

  public FeedbackLabel getLabel() {
    return label;
  }

  public void setLabel(FeedbackLabel label) {
    this.label = label;
  }

  public FeedbackSource getSource() {
    return source;
  }

  public void setSource(FeedbackSource source) {
    this.source = source;
  }

  public String getDecisionAction() {
    return decisionAction;
  }

  public void setDecisionAction(String decisionAction) {
    this.decisionAction = decisionAction;
  }

  public Integer getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(Integer riskScore) {
    this.riskScore = riskScore;
  }
}
