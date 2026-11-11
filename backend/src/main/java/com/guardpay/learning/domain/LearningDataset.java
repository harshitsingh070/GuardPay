package com.guardpay.learning.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of admin-verified feedback used for offline evaluation.
 *
 * <p>The row carries its own policy/feature versions and content checksum so a
 * model can always be traced to exactly what it was trained on.
 */
@Entity
@Table(name = "learning_datasets")
public class LearningDataset extends BaseEntity {

  @Column(name = "dataset_version", length = 64, nullable = false, unique = true)
  private String datasetVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 24, nullable = false)
  private DatasetStatus status = DatasetStatus.READY;

  @Column(name = "feature_version", length = 32, nullable = false)
  private String featureVersion;

  @Column(name = "checksum", length = 64, nullable = false, unique = true)
  private String checksum;

  @Column(name = "record_count", nullable = false)
  private int recordCount;

  @Column(name = "fraud_count", nullable = false)
  private int fraudCount;

  @Column(name = "legitimate_count", nullable = false)
  private int legitimateCount;

  @Column(name = "train_count", nullable = false)
  private int trainCount;

  @Column(name = "validation_count", nullable = false)
  private int validationCount;

  @Column(name = "test_count", nullable = false)
  private int testCount;

  @Column(name = "excluded_count", nullable = false)
  private int excludedCount;

  @Column(name = "label_policy_version", length = 32, nullable = false)
  private String labelPolicyVersion;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "built_at", nullable = false)
  private OffsetDateTime builtAt;

  @Column(name = "invalidated_at")
  private OffsetDateTime invalidatedAt;

  @Column(name = "invalidation_reason", length = 500)
  private String invalidationReason;

  public String getDatasetVersion() {
    return datasetVersion;
  }

  public void setDatasetVersion(String datasetVersion) {
    this.datasetVersion = datasetVersion;
  }

  public DatasetStatus getStatus() {
    return status;
  }

  public void setStatus(DatasetStatus status) {
    this.status = status;
  }

  public String getFeatureVersion() {
    return featureVersion;
  }

  public void setFeatureVersion(String featureVersion) {
    this.featureVersion = featureVersion;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }

  public int getRecordCount() {
    return recordCount;
  }

  public void setRecordCount(int recordCount) {
    this.recordCount = recordCount;
  }

  public int getFraudCount() {
    return fraudCount;
  }

  public void setFraudCount(int fraudCount) {
    this.fraudCount = fraudCount;
  }

  public int getLegitimateCount() {
    return legitimateCount;
  }

  public void setLegitimateCount(int legitimateCount) {
    this.legitimateCount = legitimateCount;
  }

  public int getTrainCount() {
    return trainCount;
  }

  public void setTrainCount(int trainCount) {
    this.trainCount = trainCount;
  }

  public int getValidationCount() {
    return validationCount;
  }

  public void setValidationCount(int validationCount) {
    this.validationCount = validationCount;
  }

  public int getTestCount() {
    return testCount;
  }

  public void setTestCount(int testCount) {
    this.testCount = testCount;
  }

  public int getExcludedCount() {
    return excludedCount;
  }

  public void setExcludedCount(int excludedCount) {
    this.excludedCount = excludedCount;
  }

  public String getLabelPolicyVersion() {
    return labelPolicyVersion;
  }

  public void setLabelPolicyVersion(String labelPolicyVersion) {
    this.labelPolicyVersion = labelPolicyVersion;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public OffsetDateTime getBuiltAt() {
    return builtAt;
  }

  public void setBuiltAt(OffsetDateTime builtAt) {
    this.builtAt = builtAt;
  }

  public OffsetDateTime getInvalidatedAt() {
    return invalidatedAt;
  }

  public void setInvalidatedAt(OffsetDateTime invalidatedAt) {
    this.invalidatedAt = invalidatedAt;
  }

  public String getInvalidationReason() {
    return invalidationReason;
  }

  public void setInvalidationReason(String invalidationReason) {
    this.invalidationReason = invalidationReason;
  }

  public boolean isUsableForTraining() {
    return status == DatasetStatus.READY;
  }
}
