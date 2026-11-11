package com.guardpay.learning.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable audit trail for dataset, training, approval, deployment, rollback. */
@Entity
@Table(name = "learning_events")
public class LearningEvent extends BaseEntity {

  @Column(name = "dataset_id")
  private UUID datasetId;

  @Column(name = "model_id")
  private UUID modelId;

  @Column(name = "actor_reference", nullable = false)
  private UUID actorReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", length = 40, nullable = false)
  private LearningEventType eventType;

  @Column(name = "detail", length = 1000)
  private String detail;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  public UUID getDatasetId() {
    return datasetId;
  }

  public void setDatasetId(UUID datasetId) {
    this.datasetId = datasetId;
  }

  public UUID getModelId() {
    return modelId;
  }

  public void setModelId(UUID modelId) {
    this.modelId = modelId;
  }

  public UUID getActorReference() {
    return actorReference;
  }

  public void setActorReference(UUID actorReference) {
    this.actorReference = actorReference;
  }

  public LearningEventType getEventType() {
    return eventType;
  }

  public void setEventType(LearningEventType eventType) {
    this.eventType = eventType;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }
}
