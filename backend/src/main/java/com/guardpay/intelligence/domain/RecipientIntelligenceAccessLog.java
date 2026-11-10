package com.guardpay.intelligence.domain;

import com.guardpay.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Audit trail for explicit intelligence access (not every payment lookup). */
@Entity
@Table(name = "recipient_intelligence_access_logs")
public class RecipientIntelligenceAccessLog extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "recipient_entity_id",
      foreignKey = @ForeignKey(name = "fk_access_logs_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Column(name = "actor_reference", nullable = false)
  private UUID actorReference;

  @Column(name = "access_type", length = 32, nullable = false)
  private String accessType;

  @Column(name = "outcome", length = 32, nullable = false)
  private String outcome;

  @Column(name = "accessed_at", nullable = false)
  private OffsetDateTime accessedAt;

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

  public String getAccessType() {
    return accessType;
  }

  public void setAccessType(String accessType) {
    this.accessType = accessType;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public OffsetDateTime getAccessedAt() {
    return accessedAt;
  }

  public void setAccessedAt(OffsetDateTime accessedAt) {
    this.accessedAt = accessedAt;
  }
}
