package com.guardpay.recipient;

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

/**
 * A payment recipient known to exactly one user.
 *
 * <p>Stores identifiers only — never payment credentials.
 * {@code paypalAccountId} is an external PayPal account identifier.
 */
@Entity
@Table(name = "recipients")
public class Recipient extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_recipients_user"))
  private User user;

  @Column(name = "name")
  private String name;

  @Column(name = "email")
  private String email;

  @Column(name = "paypal_account_id", length = 128)
  private String paypalAccountId;

  @Column(name = "is_trusted", nullable = false)
  private boolean trusted = false;

  @Column(name = "first_seen_at")
  private OffsetDateTime firstSeenAt;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPaypalAccountId() {
    return paypalAccountId;
  }

  public void setPaypalAccountId(String paypalAccountId) {
    this.paypalAccountId = paypalAccountId;
  }

  public boolean isTrusted() {
    return trusted;
  }

  public void setTrusted(boolean trusted) {
    this.trusted = trusted;
  }

  public OffsetDateTime getFirstSeenAt() {
    return firstSeenAt;
  }

  public void setFirstSeenAt(OffsetDateTime firstSeenAt) {
    this.firstSeenAt = firstSeenAt;
  }
}
