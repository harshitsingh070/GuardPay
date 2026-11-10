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

/**
 * One hashed identifier belonging to a global recipient entity.
 *
 * <p>The HMAC digest is unique per identifier type, which lets a lookup find
 * the entity without ever storing the raw value. Multiple key versions are
 * allowed during rotation.
 */
@Entity
@Table(
    name = "recipient_identifiers",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_recipient_identifier_hmac",
            columnNames = {"identifier_type", "identifier_hmac"}))
public class RecipientIdentifier extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "recipient_entity_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_recipient_identifiers_entity"))
  private GlobalRecipientEntity recipientEntity;

  @Enumerated(EnumType.STRING)
  @Column(name = "identifier_type", length = 32, nullable = false)
  private IdentifierType identifierType;

  @Column(name = "identifier_hmac", length = 64, nullable = false)
  private String identifierHmac;

  @Column(name = "hmac_key_version", nullable = false)
  private int hmacKeyVersion;

  @Column(name = "normalization_version", length = 32, nullable = false)
  private String normalizationVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", length = 16, nullable = false)
  private IdentifierVerificationStatus verificationStatus =
      IdentifierVerificationStatus.UNVERIFIED;

  @Enumerated(EnumType.STRING)
  @Column(name = "identity_confidence", length = 16, nullable = false)
  private IdentityConfidence identityConfidence = IdentityConfidence.LOW;

  @Column(name = "first_seen_at", nullable = false)
  private OffsetDateTime firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private OffsetDateTime lastSeenAt;

  public GlobalRecipientEntity getRecipientEntity() {
    return recipientEntity;
  }

  public void setRecipientEntity(GlobalRecipientEntity recipientEntity) {
    this.recipientEntity = recipientEntity;
  }

  public IdentifierType getIdentifierType() {
    return identifierType;
  }

  public void setIdentifierType(IdentifierType identifierType) {
    this.identifierType = identifierType;
  }

  public String getIdentifierHmac() {
    return identifierHmac;
  }

  public void setIdentifierHmac(String identifierHmac) {
    this.identifierHmac = identifierHmac;
  }

  public int getHmacKeyVersion() {
    return hmacKeyVersion;
  }

  public void setHmacKeyVersion(int hmacKeyVersion) {
    this.hmacKeyVersion = hmacKeyVersion;
  }

  public String getNormalizationVersion() {
    return normalizationVersion;
  }

  public void setNormalizationVersion(String normalizationVersion) {
    this.normalizationVersion = normalizationVersion;
  }

  public IdentifierVerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public void setVerificationStatus(IdentifierVerificationStatus verificationStatus) {
    this.verificationStatus = verificationStatus;
  }

  public IdentityConfidence getIdentityConfidence() {
    return identityConfidence;
  }

  public void setIdentityConfidence(IdentityConfidence identityConfidence) {
    this.identityConfidence = identityConfidence;
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
}
