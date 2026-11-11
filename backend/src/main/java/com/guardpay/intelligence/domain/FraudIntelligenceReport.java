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
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user-submitted fraud report against a global recipient entity.
 *
 * <p>The reporter reference is the internal user id and is never exposed to
 * other users. The dedup fingerprint prevents the same reporter from creating
 * duplicate rows for the same entity within a day. Raw report text is never
 * stored: only structured metadata and a stable fingerprint.
 */
@Entity
@Table(
    name = "fraud_intelligence_reports",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_fraud_report_dedup",
            columnNames = {
              "recipient_entity_id", "reporter_reference", "evidence_fingerprint"
            }))
public class FraudIntelligenceReport extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_fraud_reports_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Column(name = "reporter_reference", nullable = false)
  private UUID reporterReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", length = 16, nullable = false)
  private ReportSource source = ReportSource.USER;

  @Enumerated(EnumType.STRING)
  @Column(name = "report_status", length = 16, nullable = false)
  private ReportStatus reportStatus = ReportStatus.SUBMITTED;

  @Column(name = "evidence_fingerprint", length = 64, nullable = false)
  private String evidenceFingerprint;

  @Column(name = "related_transaction_id")
  private UUID relatedTransactionId;

  @Column(name = "incident_at")
  private OffsetDateTime incidentAt;

  @Column(name = "review_note", columnDefinition = "TEXT")
  private String reviewNote;

  @Column(name = "reviewed_at")
  private OffsetDateTime reviewedAt;

  @Column(name = "reviewer_reference")
  private UUID reviewerReference;

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public UUID getReporterReference() {
    return reporterReference;
  }

  public void setReporterReference(UUID reporterReference) {
    this.reporterReference = reporterReference;
  }

  public ReportSource getSource() {
    return source;
  }

  public void setSource(ReportSource source) {
    this.source = source;
  }

  public ReportStatus getReportStatus() {
    return reportStatus;
  }

  public void setReportStatus(ReportStatus reportStatus) {
    this.reportStatus = reportStatus;
  }

  public String getEvidenceFingerprint() {
    return evidenceFingerprint;
  }

  public void setEvidenceFingerprint(String evidenceFingerprint) {
    this.evidenceFingerprint = evidenceFingerprint;
  }

  public UUID getRelatedTransactionId() {
    return relatedTransactionId;
  }

  public void setRelatedTransactionId(UUID relatedTransactionId) {
    this.relatedTransactionId = relatedTransactionId;
  }

  public OffsetDateTime getIncidentAt() {
    return incidentAt;
  }

  public void setIncidentAt(OffsetDateTime incidentAt) {
    this.incidentAt = incidentAt;
  }

  public String getReviewNote() {
    return reviewNote;
  }

  public void setReviewNote(String reviewNote) {
    this.reviewNote = reviewNote;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(OffsetDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public UUID getReviewerReference() {
    return reviewerReference;
  }

  public void setReviewerReference(UUID reviewerReference) {
    this.reviewerReference = reviewerReference;
  }
}
