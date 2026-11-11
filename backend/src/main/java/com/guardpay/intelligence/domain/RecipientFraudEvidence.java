package com.guardpay.intelligence.domain;

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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One deduplicated piece of fraud evidence for a global recipient entity.
 *
 * <p>Repeat observations of the same fact increment {@code reportCount}
 * instead of creating independent rows, so spam cannot inflate the score.
 * Only structured evidence metadata is stored; free-text notes are never
 * persisted here.
 */
@Entity
@Table(
    name = "recipient_fraud_evidence",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_recipient_evidence_fingerprint",
            columnNames = {"recipient_entity_id", "evidence_fingerprint"}))
public class RecipientFraudEvidence extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_evidence_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Enumerated(EnumType.STRING)
  @Column(name = "evidence_type", length = 32, nullable = false)
  private EvidenceType evidenceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 32, nullable = false)
  private EvidenceSource source;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", length = 16, nullable = false)
  private EvidenceVerificationStatus verificationStatus = EvidenceVerificationStatus.UNVERIFIED;

  @Column(name = "confidence", precision = 5, scale = 4, nullable = false)
  private BigDecimal confidence = BigDecimal.valueOf(0.5);

  @Column(name = "evidence_fingerprint", length = 64, nullable = false)
  private String evidenceFingerprint;

  @Column(name = "reporter_group_key", length = 64)
  private String reporterGroupKey;

  @Column(name = "related_transaction_id")
  private UUID relatedTransactionId;

  @Column(name = "observed_at", nullable = false)
  private OffsetDateTime observedAt;

  @Column(name = "last_observed_at", nullable = false)
  private OffsetDateTime lastObservedAt;

  @Column(name = "report_count", nullable = false)
  private int reportCount = 1;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "revoked_reason", length = 255)
  private String revokedReason;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "source_report_id",
      foreignKey = @ForeignKey(name = "fk_recipient_evidence_report"))
  private FraudIntelligenceReport sourceReport;

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public EvidenceType getEvidenceType() {
    return evidenceType;
  }

  public void setEvidenceType(EvidenceType evidenceType) {
    this.evidenceType = evidenceType;
  }

  public EvidenceSource getSource() {
    return source;
  }

  public void setSource(EvidenceSource source) {
    this.source = source;
  }

  public EvidenceVerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public void setVerificationStatus(EvidenceVerificationStatus verificationStatus) {
    this.verificationStatus = verificationStatus;
  }

  public BigDecimal getConfidence() {
    return confidence;
  }

  public void setConfidence(BigDecimal confidence) {
    this.confidence = confidence;
  }

  public String getEvidenceFingerprint() {
    return evidenceFingerprint;
  }

  public void setEvidenceFingerprint(String evidenceFingerprint) {
    this.evidenceFingerprint = evidenceFingerprint;
  }

  public String getReporterGroupKey() {
    return reporterGroupKey;
  }

  public void setReporterGroupKey(String reporterGroupKey) {
    this.reporterGroupKey = reporterGroupKey;
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

  public OffsetDateTime getLastObservedAt() {
    return lastObservedAt;
  }

  public void setLastObservedAt(OffsetDateTime lastObservedAt) {
    this.lastObservedAt = lastObservedAt;
  }

  public int getReportCount() {
    return reportCount;
  }

  public void setReportCount(int reportCount) {
    this.reportCount = reportCount;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getRevokedReason() {
    return revokedReason;
  }

  public void setRevokedReason(String revokedReason) {
    this.revokedReason = revokedReason;
  }

  public FraudIntelligenceReport getSourceReport() {
    return sourceReport;
  }

  public void setSourceReport(FraudIntelligenceReport sourceReport) {
    this.sourceReport = sourceReport;
  }
}
