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
 * Versioned rule-calibration candidate.
 *
 * <p>This is deliberately not an opaque black-box: the artifact is a small,
 * inspectable set of score thresholds. It can only influence decisions after
 * an explicit approval + deployment, and rollback restores the previous active
 * version.
 */
@Entity
@Table(name = "learning_models")
public class LearningModel extends BaseEntity {

  @Column(name = "model_version", length = 64, nullable = false, unique = true)
  private String modelVersion;

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(name = "model_type", length = 40, nullable = false)
  private String modelType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 24, nullable = false)
  private ModelStatus status = ModelStatus.CANDIDATE;

  @Column(name = "threshold_score")
  private Integer thresholdScore;

  @Column(name = "precision_value")
  private Double precisionValue;

  @Column(name = "recall_value")
  private Double recallValue;

  @Column(name = "f1_value")
  private Double f1Value;

  @Column(name = "accuracy_value")
  private Double accuracyValue;

  @Column(name = "false_positive_rate")
  private Double falsePositiveRate;

  @Column(name = "false_negative_rate")
  private Double falseNegativeRate;

  @Column(name = "validation_count", nullable = false)
  private int validationCount;

  @Column(name = "test_count", nullable = false)
  private int testCount;

  @Column(name = "artifact_json", columnDefinition = "TEXT", nullable = false)
  private String artifactJson;

  @Column(name = "metrics_json", columnDefinition = "TEXT")
  private String metricsJson;

  @Column(name = "champion_comparison_json", columnDefinition = "TEXT")
  private String championComparisonJson;

  @Column(name = "training_failure_reason", length = 500)
  private String trainingFailureReason;

  @Column(name = "dataset_checksum", length = 64)
  private String datasetChecksum;

  @Column(name = "label_policy_version", length = 32)
  private String labelPolicyVersion;

  @Column(name = "feature_version", length = 32)
  private String featureVersion;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "activated_by")
  private UUID activatedBy;

  @Column(name = "approved_at")
  private OffsetDateTime approvedAt;

  @Column(name = "activated_at")
  private OffsetDateTime activatedAt;

  @Column(name = "retired_at")
  private OffsetDateTime retiredAt;

  @Column(name = "rollback_reference")
  private UUID rollbackReference;

  public String getModelVersion() {
    return modelVersion;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public UUID getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(UUID datasetId) {
    this.datasetId = datasetId;
  }

  public String getModelType() {
    return modelType;
  }

  public void setModelType(String modelType) {
    this.modelType = modelType;
  }

  public ModelStatus getStatus() {
    return status;
  }

  public void setStatus(ModelStatus status) {
    this.status = status;
  }

  public Integer getThresholdScore() {
    return thresholdScore;
  }

  public void setThresholdScore(Integer thresholdScore) {
    this.thresholdScore = thresholdScore;
  }

  public Double getPrecisionValue() {
    return precisionValue;
  }

  public void setPrecisionValue(Double precisionValue) {
    this.precisionValue = precisionValue;
  }

  public Double getRecallValue() {
    return recallValue;
  }

  public void setRecallValue(Double recallValue) {
    this.recallValue = recallValue;
  }

  public Double getF1Value() {
    return f1Value;
  }

  public void setF1Value(Double f1Value) {
    this.f1Value = f1Value;
  }

  public Double getAccuracyValue() {
    return accuracyValue;
  }

  public void setAccuracyValue(Double accuracyValue) {
    this.accuracyValue = accuracyValue;
  }

  public Double getFalsePositiveRate() {
    return falsePositiveRate;
  }

  public void setFalsePositiveRate(Double falsePositiveRate) {
    this.falsePositiveRate = falsePositiveRate;
  }

  public Double getFalseNegativeRate() {
    return falseNegativeRate;
  }

  public void setFalseNegativeRate(Double falseNegativeRate) {
    this.falseNegativeRate = falseNegativeRate;
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

  public String getArtifactJson() {
    return artifactJson;
  }

  public void setArtifactJson(String artifactJson) {
    this.artifactJson = artifactJson;
  }

  public String getMetricsJson() {
    return metricsJson;
  }

  public void setMetricsJson(String metricsJson) {
    this.metricsJson = metricsJson;
  }

  public String getChampionComparisonJson() {
    return championComparisonJson;
  }

  public void setChampionComparisonJson(String championComparisonJson) {
    this.championComparisonJson = championComparisonJson;
  }

  public String getTrainingFailureReason() {
    return trainingFailureReason;
  }

  public void setTrainingFailureReason(String trainingFailureReason) {
    this.trainingFailureReason = trainingFailureReason;
  }

  public String getDatasetChecksum() {
    return datasetChecksum;
  }

  public void setDatasetChecksum(String datasetChecksum) {
    this.datasetChecksum = datasetChecksum;
  }

  public String getLabelPolicyVersion() {
    return labelPolicyVersion;
  }

  public void setLabelPolicyVersion(String labelPolicyVersion) {
    this.labelPolicyVersion = labelPolicyVersion;
  }

  public String getFeatureVersion() {
    return featureVersion;
  }

  public void setFeatureVersion(String featureVersion) {
    this.featureVersion = featureVersion;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(UUID approvedBy) {
    this.approvedBy = approvedBy;
  }

  public UUID getActivatedBy() {
    return activatedBy;
  }

  public void setActivatedBy(UUID activatedBy) {
    this.activatedBy = activatedBy;
  }

  public OffsetDateTime getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(OffsetDateTime approvedAt) {
    this.approvedAt = approvedAt;
  }

  public OffsetDateTime getActivatedAt() {
    return activatedAt;
  }

  public void setActivatedAt(OffsetDateTime activatedAt) {
    this.activatedAt = activatedAt;
  }

  public OffsetDateTime getRetiredAt() {
    return retiredAt;
  }

  public void setRetiredAt(OffsetDateTime retiredAt) {
    this.retiredAt = retiredAt;
  }

  public UUID getRollbackReference() {
    return rollbackReference;
  }

  public void setRollbackReference(UUID rollbackReference) {
    this.rollbackReference = rollbackReference;
  }
}
