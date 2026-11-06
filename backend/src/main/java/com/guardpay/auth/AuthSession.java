package com.guardpay.auth;

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

/** Opaque server-side session. Only a SHA-256 hash of the cookie token is stored. */
@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_auth_sessions_user"))
  private User user;

  @Column(name = "token_hash", length = 64, nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "last_used_at", nullable = false)
  private OffsetDateTime lastUsedAt;

  @Column(name = "absolute_expires_at", nullable = false)
  private OffsetDateTime absoluteExpiresAt;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  public OffsetDateTime getAbsoluteExpiresAt() {
    return absoluteExpiresAt;
  }

  public void setAbsoluteExpiresAt(OffsetDateTime absoluteExpiresAt) {
    this.absoluteExpiresAt = absoluteExpiresAt;
  }
}
