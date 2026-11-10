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
import java.util.UUID;

/** Append-only admin review trail for a global recipient entity. */
@Entity
@Table(name = "recipient_review_events")
public class RecipientReviewEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_review_events_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Column(name = "actor_reference", nullable = false)
  private UUID actorReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", length = 32, nullable = false)
  private ReviewAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 32)
  private ReputationStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", length = 32, nullable = false)
  private ReputationStatus toStatus;

  @Column(name = "note", columnDefinition = "TEXT")
  private String note;

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public UUID getActorReference() {
    return actorReference;
  }

  public void setActorReference(UUID actorReference) {
    this.actorReference = actorReference;
  }

  public ReviewAction getAction() {
    return action;
  }

  public void setAction(ReviewAction action) {
    this.action = action;
  }

  public ReputationStatus getFromStatus() {
    return fromStatus;
  }

  public void setFromStatus(ReputationStatus fromStatus) {
    this.fromStatus = fromStatus;
  }

  public ReputationStatus getToStatus() {
    return toStatus;
  }

  public void setToStatus(ReputationStatus toStatus) {
    this.toStatus = toStatus;
  }

  public String getNote() {
    return note;
  }

  public void setNote(String note) {
    this.note = note;
  }
}
