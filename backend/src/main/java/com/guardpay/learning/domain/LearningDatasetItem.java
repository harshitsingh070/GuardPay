package com.guardpay.learning.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One eligible, decision-time feature snapshot.
 *
 * <p>Feature JSON is reconstructed only from the persisted assessment. The
 * fraud label is stored separately and is never part of the feature payload.
 */
@Entity
@Table(
    name = "learning_dataset_items",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_learning_item_dataset_feedback",
            columnNames = {"dataset_id", "feedback_id"}))
public class LearningDatasetItem extends BaseEntity {

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(name = "feedback_id", nullable = false)
  private UUID feedbackId;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "label", length = 16, nullable = false)
  private String label;

  @Column(name = "split_name", length = 16, nullable = false)
  private String splitName;

  @Column(name = "decision_action", length = 16)
  private String decisionAction;

  @Column(name = "risk_score")
  private Integer riskScore;

  @Column(name = "correlation_contribution", nullable = false)
  private int correlationContribution;

  @Column(name = "recipient_reputation_points", nullable = false)
  private int recipientReputationPoints;

  @Column(name = "finding_codes", columnDefinition = "TEXT")
  private String findingCodes;

  @Column(name = "feature_json", columnDefinition = "TEXT", nullable = false)
  private String featureJson;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  public UUID getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(UUID datasetId) {
    this.datasetId = datasetId;
  }

  public UUID getFeedbackId() {
    return feedbackId;
  }

  public void setFeedbackId(UUID feedbackId) {
    this.feedbackId = feedbackId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(UUID transactionId) {
    this.transactionId = transactionId;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getSplitName() {
    return splitName;
  }

  public void setSplitName(String splitName) {
    this.splitName = splitName;
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

  public int getCorrelationContribution() {
    return correlationContribution;
  }

  public void setCorrelationContribution(int correlationContribution) {
    this.correlationContribution = correlationContribution;
  }

  public int getRecipientReputationPoints() {
    return recipientReputationPoints;
  }

  public void setRecipientReputationPoints(int recipientReputationPoints) {
    this.recipientReputationPoints = recipientReputationPoints;
  }

  public String getFindingCodes() {
    return findingCodes;
  }

  public void setFindingCodes(String findingCodes) {
    this.findingCodes = findingCodes;
  }

  public String getFeatureJson() {
    return featureJson;
  }

  public void setFeatureJson(String featureJson) {
    this.featureJson = featureJson;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
