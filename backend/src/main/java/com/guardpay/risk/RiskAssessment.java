package com.guardpay.risk;

import com.guardpay.common.domain.BaseEntity;
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

/**
 * A persisted risk evaluation for a transaction. A transaction may have many
 * assessments (re-analysis over time).
 *
 * <p>{@code score} is the GuardPay risk score (0–100 heuristic) — it is NOT a
 * fraud probability. The deterministic engine that computes it arrives in a
 * later milestone; this entity only stores its output.
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "transaction_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_risk_assessments_transaction"))
  private Transaction transaction;

  @Column(name = "score", nullable = false)
  private int score;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_level", length = 16, nullable = false)
  private RiskLevel riskLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "recommended_action", length = 16, nullable = false)
  private RecommendedAction recommendedAction;

  @Column(name = "explanation", columnDefinition = "TEXT")
  private String explanation;

  /**
   * JSON array of the engine findings behind the decision (audit/display only).
   * Stored as text so this module never depends on a JSON column type.
   */
  @Column(name = "findings", columnDefinition = "TEXT")
  private String findings;

  /** JSON array of correlation findings (audit/display only). */
  @Column(name = "correlation_findings", columnDefinition = "TEXT")
  private String correlationFindings;

  /** Correlation status at decision time (AVAILABLE, NO_MATCH, ...). */
  @Column(name = "correlation_status", length = 16)
  private String correlationStatus;

  /** Points the correlation layer added to the score. */
  @Column(name = "correlation_contribution", nullable = false)
  private int correlationContribution;

  /** JSON object with identifier-free global reputation evidence (audit/display only). */
  @Column(name = "recipient_reputation", columnDefinition = "TEXT")
  private String recipientReputation;

  /** Points the global reputation layer added to the score. */
  @Column(name = "recipient_reputation_points", nullable = false)
  private int recipientReputationPoints;

  public String getRecipientReputation() {
    return recipientReputation;
  }

  public void setRecipientReputation(String recipientReputation) {
    this.recipientReputation = recipientReputation;
  }

  public int getRecipientReputationPoints() {
    return recipientReputationPoints;
  }

  public void setRecipientReputationPoints(int recipientReputationPoints) {
    this.recipientReputationPoints = recipientReputationPoints;
  }

  public String getFindings() {
    return findings;
  }

  public void setFindings(String findings) {
    this.findings = findings;
  }

  public String getCorrelationFindings() {
    return correlationFindings;
  }

  public void setCorrelationFindings(String correlationFindings) {
    this.correlationFindings = correlationFindings;
  }

  public String getCorrelationStatus() {
    return correlationStatus;
  }

  public void setCorrelationStatus(String correlationStatus) {
    this.correlationStatus = correlationStatus;
  }

  public int getCorrelationContribution() {
    return correlationContribution;
  }

  public void setCorrelationContribution(int correlationContribution) {
    this.correlationContribution = correlationContribution;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel;
  }

  public RecommendedAction getRecommendedAction() {
    return recommendedAction;
  }

  public void setRecommendedAction(RecommendedAction recommendedAction) {
    this.recommendedAction = recommendedAction;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }
}
