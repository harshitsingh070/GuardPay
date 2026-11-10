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
 * Per-user relationship to a global recipient entity.
 *
 * <p>Used for aggregate analytics and for proving that a user has a legitimate
 * relationship before the intelligence summary is returned.
 */
@Entity
@Table(
    name = "recipient_relationships",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_recipient_relationship",
            columnNames = {"owner_reference", "recipient_entity_id"}))
public class RecipientRelationship extends BaseEntity {

  @Column(name = "owner_reference", nullable = false)
  private UUID ownerReference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_relationships_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship_type", length = 32, nullable = false)
  private RelationshipType relationshipType;

  @Enumerated(EnumType.STRING)
  @Column(name = "confidence", length = 16, nullable = false)
  private RelationshipConfidence confidence;

  @Column(name = "first_seen_at", nullable = false)
  private OffsetDateTime firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private OffsetDateTime lastSeenAt;

  @Column(name = "interaction_count", nullable = false)
  private int interactionCount = 1;

  public UUID getOwnerReference() {
    return ownerReference;
  }

  public void setOwnerReference(UUID ownerReference) {
    this.ownerReference = ownerReference;
  }

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public RelationshipType getRelationshipType() {
    return relationshipType;
  }

  public void setRelationshipType(RelationshipType relationshipType) {
    this.relationshipType = relationshipType;
  }

  public RelationshipConfidence getConfidence() {
    return confidence;
  }

  public void setConfidence(RelationshipConfidence confidence) {
    this.confidence = confidence;
  }

  public OffsetDateTime getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(OffsetDateTime firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public int getInteractionCount() {
    return interactionCount;
  }

  public void setInteractionCount(int interactionCount) {
    this.interactionCount = interactionCount;
  }
}
