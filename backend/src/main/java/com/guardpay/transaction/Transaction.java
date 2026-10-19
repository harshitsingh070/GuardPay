package com.guardpay.transaction;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.merchant.Merchant;
import com.guardpay.recipient.Recipient;
import com.guardpay.user.User;
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

/**
 * A GuardPay-tracked payment. Money uses {@link BigDecimal} only —
 * never {@code float}/{@code double} (maps to {@code NUMERIC(19,4)}).
 *
 * <p>Recipient/merchant references are nullable; so is the PayPal ID
 * (local/demo transactions may not have one yet).
 */
@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_transactions_user"))
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recipient_id", foreignKey = @ForeignKey(name = "fk_transactions_recipient"))
  private Recipient recipient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "merchant_id", foreignKey = @ForeignKey(name = "fk_transactions_merchant"))
  private Merchant merchant;

  @Column(name = "paypal_transaction_id", length = 128)
  private String paypalTransactionId;

  @Column(name = "paypal_order_id", length = 128, unique = true)
  private String paypalOrderId;

  @Column(name = "paypal_capture_id", length = 128)
  private String paypalCaptureId;

  @Column(name = "amount", precision = 19, scale = 4, nullable = false)
  private BigDecimal amount;

  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "transaction_type", length = 16, nullable = false)
  private TransactionType transactionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private TransactionStatus status;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "transaction_timestamp", nullable = false)
  private OffsetDateTime transactionTimestamp;

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Recipient getRecipient() {
    return recipient;
  }

  public void setRecipient(Recipient recipient) {
    this.recipient = recipient;
  }

  public Merchant getMerchant() {
    return merchant;
  }

  public void setMerchant(Merchant merchant) {
    this.merchant = merchant;
  }

  public String getPaypalTransactionId() {
    return paypalTransactionId;
  }

  public void setPaypalTransactionId(String paypalTransactionId) {
    this.paypalTransactionId = paypalTransactionId;
  }

  public String getPaypalOrderId() {
    return paypalOrderId;
  }

  public void setPaypalOrderId(String paypalOrderId) {
    this.paypalOrderId = paypalOrderId;
  }

  public String getPaypalCaptureId() {
    return paypalCaptureId;
  }

  public void setPaypalCaptureId(String paypalCaptureId) {
    this.paypalCaptureId = paypalCaptureId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public TransactionType getTransactionType() {
    return transactionType;
  }

  public void setTransactionType(TransactionType transactionType) {
    this.transactionType = transactionType;
  }

  public TransactionStatus getStatus() {
    return status;
  }

  public void setStatus(TransactionStatus status) {
    this.status = status;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public OffsetDateTime getTransactionTimestamp() {
    return transactionTimestamp;
  }

  public void setTransactionTimestamp(OffsetDateTime transactionTimestamp) {
    this.transactionTimestamp = transactionTimestamp;
  }
}
