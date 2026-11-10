package com.guardpay.intelligence.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 * Global (cross-user) identity for a payment recipient.
 *
 * <p>Contains no raw identifiers and no free text. Status, category, and score
 * are derived from evidence by the reputation service; this row is the shared
 * lookup anchor for every user who pays the same recipient.
 */
@Entity
@Table(name = "global_recipient_entities")
public class GlobalRecipientEntity extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", length = 16, nullable = false)
  private RecipientEntityType entityType = RecipientEntityType.UNKNOWN;

  @Enumerated(EnumType.STRING)
  @Column(name = "reputation_status", length = 32, nullable = false)
  private ReputationStatus reputationStatus = ReputationStatus.UNKNOWN;

  @Enumerated(EnumType.STRING)
  @Column(name = "reputation_category", length = 32, nullable = false)
  private ReputationCategory reputationCategory = ReputationCategory.NO_DATA;

  @Column(name = "reputation_score", nullable = false)
  private int reputationScore;

  @Enumerated(EnumType.STRING)
  @Column(name = "identity_confidence", length = 16, nullable = false)
  private IdentityConfidence identityConfidence = IdentityConfidence.LOW;

  @Column(name = "evidence_count", nullable = false)
  private int evidenceCount;

  @Column(name = "report_count", nullable = false)
  private int reportCount;

  @Column(name = "distinct_reporter_count", nullable = false)
  private int distinctReporterCount;

  @Column(name = "last_evidence_at")
  private OffsetDateTime lastEvidenceAt;

  @Column(name = "last_calculated_at")
  private OffsetDateTime lastCalculatedAt;

  @Column(name = "calculation_version", length = 16)
  private String calculationVersion;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  public RecipientEntityType getEntityType() {
    return entityType;
  }

  public void setEntityType(RecipientEntityType entityType) {
    this.entityType = entityType;
  }

  public ReputationStatus getReputationStatus() {
    return reputationStatus;
  }

  public void setReputationStatus(ReputationStatus reputationStatus) {
    this.reputationStatus = reputationStatus;
  }

  public ReputationCategory getReputationCategory() {
    return reputationCategory;
  }

  public void setReputationCategory(ReputationCategory reputationCategory) {
    this.reputationCategory = reputationCategory;
  }

  public int getReputationScore() {
    return reputationScore;
  }

  public void setReputationScore(int reputationScore) {
    this.reputationScore = reputationScore;
  }

  public IdentityConfidence getIdentityConfidence() {
    return identityConfidence;
  }

  public void setIdentityConfidence(IdentityConfidence identityConfidence) {
    this.identityConfidence = identityConfidence;
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

  public OffsetDateTime getLastEvidenceAt() {
    return lastEvidenceAt;
  }

  public void setLastEvidenceAt(OffsetDateTime lastEvidenceAt) {
    this.lastEvidenceAt = lastEvidenceAt;
  }

  public OffsetDateTime getLastCalculatedAt() {
    return lastCalculatedAt;
  }

  public void setLastCalculatedAt(OffsetDateTime lastCalculatedAt) {
    this.lastCalculatedAt = lastCalculatedAt;
  }

  public String getCalculationVersion() {
    return calculationVersion;
  }

  public void setCalculationVersion(String calculationVersion) {
    this.calculationVersion = calculationVersion;
  }

  public long getVersion() {
    return version;
  }
}
