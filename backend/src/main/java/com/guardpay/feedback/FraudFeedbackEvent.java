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
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only audit event for a feedback label. */
@Entity
@Table(name = "fraud_feedback_events")
public class FraudFeedbackEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "feedback_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_feedback_events_feedback"))
  private FraudFeedback feedback;

  @Column(name = "actor_reference")
  private UUID actorReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", length = 32, nullable = false)
  private FeedbackEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 24)
  private FeedbackStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 24)
  private FeedbackStatus toStatus;

  @Column(name = "detail", length = 1000)
  private String detail;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  public FraudFeedback getFeedback() {
    return feedback;
  }

  public void setFeedback(FraudFeedback feedback) {
    this.feedback = feedback;
  }

  public UUID getActorReference() {
    return actorReference;
  }

  public void setActorReference(UUID actorReference) {
    this.actorReference = actorReference;
  }

  public FeedbackEventType getEventType() {
    return eventType;
  }

  public void setEventType(FeedbackEventType eventType) {
    this.eventType = eventType;
  }

  public FeedbackStatus getFromStatus() {
    return fromStatus;
  }

  public void setFromStatus(FeedbackStatus fromStatus) {
    this.fromStatus = fromStatus;
  }

  public FeedbackStatus getToStatus() {
    return toStatus;
  }

  public void setToStatus(FeedbackStatus toStatus) {
    this.toStatus = toStatus;
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
