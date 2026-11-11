package com.guardpay.feedback;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One current ground-truth label for a transaction decision.
 *
 * <p>The decision snapshot is stored with the label so precision/recall can be
 * computed later even if the transaction is re-assessed. Labels are explicit
 * user/admin feedback, never inferred from payment status. History lives in
 * append-only event/review tables; the current row is updated on correction.
 */
@Entity
@Table(
    name = "fraud_feedback",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_fraud_feedback_transaction_user",
            columnNames = {"transaction_id", "user_reference"}))
public class FraudFeedback extends BaseEntity {

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "user_reference", nullable = false)
  private UUID userReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "label", length = 16, nullable = false)
  private FeedbackLabel label;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback_type", length = 40, nullable = false)
  private FeedbackType feedbackType;

  @Enumerated(EnumType.STRING)
  @Column(name = "feedback_status", length = 24, nullable = false)
  private FeedbackStatus feedbackStatus = FeedbackStatus.SUBMITTED;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 16, nullable = false)
  private FeedbackSource source = FeedbackSource.USER;

  @Column(name = "decision_action", length = 16)
  private String decisionAction;

  @Column(name = "risk_score")
  private Integer riskScore;

  @Column(name = "risk_assessment_id")
  private UUID riskAssessmentId;

  @Column(name = "recipient_entity_id")
  private UUID recipientEntityId;

  @Column(name = "related_finding_code", length = 64)
  private String relatedFindingCode;

  @Column(name = "reviewer_reference")
  private UUID reviewerReference;

  @Column(name = "reviewer_confidence", precision = 4, scale = 3)
  private java.math.BigDecimal reviewerConfidence;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason_code", length = 48, nullable = false)
  private FeedbackReasonCode reasonCode = FeedbackReasonCode.NONE;

  @Column(name = "evidence_fingerprint", length = 64)
  private String evidenceFingerprint;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

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

  public FeedbackType getFeedbackType() {
    return feedbackType;
  }

  public void setFeedbackType(FeedbackType feedbackType) {
    this.feedbackType = feedbackType;
  }

  public FeedbackStatus getFeedbackStatus() {
    return feedbackStatus;
  }

  public void setFeedbackStatus(FeedbackStatus feedbackStatus) {
    this.feedbackStatus = feedbackStatus;
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

  public UUID getRiskAssessmentId() {
    return riskAssessmentId;
  }

  public void setRiskAssessmentId(UUID riskAssessmentId) {
    this.riskAssessmentId = riskAssessmentId;
  }

  public UUID getRecipientEntityId() {
    return recipientEntityId;
  }

  public void setRecipientEntityId(UUID recipientEntityId) {
    this.recipientEntityId = recipientEntityId;
  }

  public String getRelatedFindingCode() {
    return relatedFindingCode;
  }

  public void setRelatedFindingCode(String relatedFindingCode) {
    this.relatedFindingCode = relatedFindingCode;
  }

  public UUID getReviewerReference() {
    return reviewerReference;
  }

  public void setReviewerReference(UUID reviewerReference) {
    this.reviewerReference = reviewerReference;
  }

  public java.math.BigDecimal getReviewerConfidence() {
    return reviewerConfidence;
  }

  public void setReviewerConfidence(java.math.BigDecimal reviewerConfidence) {
    this.reviewerConfidence = reviewerConfidence;
  }

  public FeedbackReasonCode getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(FeedbackReasonCode reasonCode) {
    this.reasonCode = reasonCode;
  }

  public String getEvidenceFingerprint() {
    return evidenceFingerprint;
  }

  public void setEvidenceFingerprint(String evidenceFingerprint) {
    this.evidenceFingerprint = evidenceFingerprint;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(OffsetDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public long getVersion() {
    return version;
  }
}
