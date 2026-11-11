package com.guardpay.learning.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Explicit record of why a feedback row did not enter a dataset version. */
@Entity
@Table(name = "learning_dataset_exclusions")
public class LearningDatasetExclusion extends BaseEntity {

  @Column(name = "dataset_id", nullable = false)
  private UUID datasetId;

  @Column(name = "feedback_id")
  private UUID feedbackId;

  @Column(name = "reason_code", length = 48, nullable = false)
  private String reasonCode;

  @Column(name = "detail", length = 500)
  private String detail;

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

  public String getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(String reasonCode) {
    this.reasonCode = reasonCode;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }
}
