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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only admin review decision for a feedback label. */
@Entity
@Table(name = "fraud_feedback_reviews")
public class FraudFeedbackReview extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "feedback_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_feedback_reviews_feedback"))
  private FraudFeedback feedback;

  @Column(name = "reviewer_reference", nullable = false)
  private UUID reviewerReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", length = 32, nullable = false)
  private FeedbackReviewAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 24, nullable = false)
  private FeedbackStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 24, nullable = false)
  private FeedbackStatus toStatus;

  @Column(name = "confidence", precision = 4, scale = 3)
  private BigDecimal confidence;

  @Column(name = "note", length = 1000)
  private String note;

  @Column(name = "reviewed_at", nullable = false)
  private OffsetDateTime reviewedAt;

  public FraudFeedback getFeedback() {
    return feedback;
  }

  public void setFeedback(FraudFeedback feedback) {
    this.feedback = feedback;
  }

  public UUID getReviewerReference() {
    return reviewerReference;
  }

  public void setReviewerReference(UUID reviewerReference) {
    this.reviewerReference = reviewerReference;
  }

  public FeedbackReviewAction getAction() {
    return action;
  }

  public void setAction(FeedbackReviewAction action) {
    this.action = action;
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

  public BigDecimal getConfidence() {
    return confidence;
  }

  public void setConfidence(BigDecimal confidence) {
    this.confidence = confidence;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public OffsetDateTime getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(OffsetDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }
}
