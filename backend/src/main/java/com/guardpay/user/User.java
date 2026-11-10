package com.guardpay.user;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.recipient.Recipient;
import com.guardpay.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * GuardPay application user (NOT a PayPal account — no payment credentials).
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Column(name = "external_user_id", length = 64, nullable = false, unique = true)
  private String externalUserId;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  /** BCrypt hash; never serialized by APIs. */
  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  /** Explicit admin flag for the intelligence review console; never client-set. */
  @Column(name = "admin", nullable = false)
  private boolean admin = false;

  /** Base32 TOTP secret; never serialized by payment APIs. */
  @Column(name = "totp_secret", length = 64)
  private String totpSecret;

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  private List<Transaction> transactions = new ArrayList<>();

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
  private List<Recipient> recipients = new ArrayList<>();

  public String getExternalUserId() {
    return externalUserId;
  }

  public void setExternalUserId(String externalUserId) {
    this.externalUserId = externalUserId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public boolean isAdmin() {
    return admin;
  }

  public void setAdmin(boolean admin) {
    this.admin = admin;
  }

  public String getTotpSecret() {
    return totpSecret;
  }

  public void setTotpSecret(String totpSecret) {
    this.totpSecret = totpSecret;
  }

  public List<Transaction> getTransactions() {
    return transactions;
  }

  public List<Recipient> getRecipients() {
    return recipients;
  }
}
