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

  public List<Transaction> getTransactions() {
    return transactions;
  }

  public List<Recipient> getRecipients() {
    return recipients;
  }
}
