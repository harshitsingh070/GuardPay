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
import java.time.OffsetDateTime;

/** Immutable audit snapshot of a reputation calculation. */
@Entity
@Table(name = "recipient_reputation_snapshots")
public class RecipientReputationSnapshot extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_reputation_snapshots_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Column(name = "score", nullable = false)
  private int score;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", length = 32, nullable = false)
  private ReputationCategory category;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 32, nullable = false)
  private ReputationStatus status;

  @Column(name = "evidence_count", nullable = false)
  private int evidenceCount;

  @Column(name = "report_count", nullable = false)
  private int reportCount;

  @Column(name = "distinct_reporter_count", nullable = false)
  private int distinctReporterCount;

  @Column(name = "calculated_at", nullable = false)
  private OffsetDateTime calculatedAt;

  @Column(name = "calculation_version", length = 16, nullable = false)
  private String calculationVersion;

  @Column(name = "contributing_evidence", columnDefinition = "TEXT")
  private String contributingEvidence;

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public ReputationCategory getCategory() {
    return category;
  }

  public void setCategory(ReputationCategory category) {
    this.category = category;
  }

  public ReputationStatus getStatus() {
    return status;
  }

  public void setStatus(ReputationStatus status) {
    this.status = status;
  }

  public int getEvidenceCount() {
    return evidenceCount;
  }

  public void setEvidenceCount(int evidenceCount) {
    this.evidenceCount = evidenceCount;
  }

  public int getReportCount() {
    return reportCount;
  }

  public void setReportCount(int reportCount) {
    this.reportCount = reportCount;
  }

  public int getDistinctReporterCount() {
    return distinctReporterCount;
  }

  public void setDistinctReporterCount(int distinctReporterCount) {
    this.distinctReporterCount = distinctReporterCount;
  }

  public OffsetDateTime getCalculatedAt() {
    return calculatedAt;
  }

  public void setCalculatedAt(OffsetDateTime calculatedAt) {
    this.calculatedAt = calculatedAt;
  }

  public String getCalculationVersion() {
    return calculationVersion;
  }

  public void setCalculationVersion(String calculationVersion) {
    this.calculationVersion = calculationVersion;
  }

  public String getContributingEvidence() {
    return contributingEvidence;
  }

  public void setContributingEvidence(String contributingEvidence) {
    this.contributingEvidence = contributingEvidence;
  }
}
