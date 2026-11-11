package com.guardpay.feedback;

import com.guardpay.common.domain.BaseEntity;
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
import java.util.UUID;

/** Structured, deduplicated evidence attached to feedback. */
@Entity
@Table(
    name = "fraud_feedback_evidence",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_feedback_evidence_fingerprint",
            columnNames = {"feedback_id", "evidence_fingerprint"}))
public class FraudFeedbackEvidence extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "feedback_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_feedback_evidence_feedback"))
  private FraudFeedback feedback;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", length = 32, nullable = false)
  private FeedbackEvidenceType evidenceType;

  @Column(name = "evidence_fingerprint", length = 64, nullable = false)
  private String evidenceFingerprint;

  @Column(name = "related_finding_code", length = 64)
  private String relatedFindingCode;

  @Column(name = "related_transaction_id")
  private UUID relatedTransactionId;

  @Column(name = "observed_at", nullable = false)
  private OffsetDateTime observedAt;

  @Column(name = "verified", nullable = false)
  private boolean verified;

  public FraudFeedback getFeedback() {
    return feedback;
  }

  public void setFeedback(FraudFeedback feedback) {
    this.feedback = feedback;
  }

  public FeedbackEvidenceType getEvidenceType() {
    return evidenceType;
  }

  public void setEvidenceType(FeedbackEvidenceType evidenceType) {
    this.evidenceType = evidenceType;
  }

  public String getEvidenceFingerprint() {
    return evidenceFingerprint;
  }

  public void setEvidenceFingerprint(String evidenceFingerprint) {
    this.evidenceFingerprint = evidenceFingerprint;
  }

  public String getRelatedFindingCode() {
    return relatedFindingCode;
  }

  public void setRelatedFindingCode(String relatedFindingCode) {
    this.relatedFindingCode = relatedFindingCode;
  }

  public UUID getRelatedTransactionId() {
    return relatedTransactionId;
  }

  public void setRelatedTransactionId(UUID relatedTransactionId) {
    this.relatedTransactionId = relatedTransactionId;
  }

  public OffsetDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(OffsetDateTime observedAt) {
    this.observedAt = observedAt;
  }

  public boolean isVerified() {
    return verified;
  }

  public void setVerified(boolean verified) {
    this.verified = verified;
  }
}
