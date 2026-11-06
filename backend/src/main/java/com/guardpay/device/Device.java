package com.guardpay.device;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A device registered by exactly one account. The raw device credential is
 * returned once at registration/rotation; only its SHA-256 hash is stored.
 */
@Entity
@Table(name = "devices")
public class Device extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_devices_user"))
  private User user;

  @Column(name = "credential_hash", length = 64, nullable = false, unique = true)
  private String credentialHash;

  @Column(name = "label", length = 120)
  private String label;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "last_seen_at")
  private OffsetDateTime lastSeenAt;

  public Device() {}

  /** Creates a device with a caller-provided stable id (validated as unused). */
  public Device(UUID id) {
    setId(id);
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getCredentialHash() {
    return credentialHash;
  }

  public void setCredentialHash(String credentialHash) {
    this.credentialHash = credentialHash;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }
}
