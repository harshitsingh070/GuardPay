package com.guardpay.payment;

import com.guardpay.common.domain.BaseEntity;
import com.guardpay.transaction.Transaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Contextual signals available when a payment is evaluated.
 *
 * <p>Exactly one context per transaction ({@code UNIQUE} transaction_id).
 * {@code activeCall} is call-state metadata only — GuardPay never records or
 * stores call audio.
 */
@Entity
@Table(name = "payment_context")
public class PaymentContext extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "transaction_id",
      nullable = false,
      unique = true,
      foreignKey = @ForeignKey(name = "fk_payment_context_transaction"))
  private Transaction transaction;

  @Column(name = "active_call", nullable = false)
  private boolean activeCall = false;

  @Column(name = "unexpected_invoice", nullable = false)
  private boolean unexpectedInvoice = false;

  @Column(name = "urgent_language", nullable = false)
  private boolean urgentLanguage = false;

  @Column(name = "suspicious_url", nullable = false)
  private boolean suspiciousUrl = false;

  @Column(name = "unknown_merchant", nullable = false)
  private boolean unknownMerchant = false;

  @Column(name = "new_recipient", nullable = false)
  private boolean newRecipient = false;

  @Column(name = "message_content", columnDefinition = "TEXT")
  private String messageContent;

  @Column(name = "invoice_content", columnDefinition = "TEXT")
  private String invoiceContent;

  @Column(name = "url", length = 2048)
  private String url;

  @Column(name = "phone_number", length = 32)
  private String phoneNumber;

  public Transaction getTransaction() {
    return transaction;
  }

  public void setTransaction(Transaction transaction) {
    this.transaction = transaction;
  }

  public boolean isActiveCall() {
    return activeCall;
  }

  public void setActiveCall(boolean activeCall) {
    this.activeCall = activeCall;
  }

  public boolean isUnexpectedInvoice() {
    return unexpectedInvoice;
  }

  public void setUnexpectedInvoice(boolean unexpectedInvoice) {
    this.unexpectedInvoice = unexpectedInvoice;
  }

  public boolean isUrgentLanguage() {
    return urgentLanguage;
  }

  public void setUrgentLanguage(boolean urgentLanguage) {
    this.urgentLanguage = urgentLanguage;
  }

  public boolean isSuspiciousUrl() {
    return suspiciousUrl;
  }

  public void setSuspiciousUrl(boolean suspiciousUrl) {
    this.suspiciousUrl = suspiciousUrl;
  }

  public boolean isUnknownMerchant() {
    return unknownMerchant;
  }

  public void setUnknownMerchant(boolean unknownMerchant) {
    this.unknownMerchant = unknownMerchant;
  }

  public boolean isNewRecipient() {
    return newRecipient;
  }

  public void setNewRecipient(boolean newRecipient) {
    this.newRecipient = newRecipient;
  }

  public String getMessageContent() {
    return messageContent;
  }

  public void setMessageContent(String messageContent) {
    this.messageContent = messageContent;
  }

  public String getInvoiceContent() {
    return invoiceContent;
  }

  public void setInvoiceContent(String invoiceContent) {
    this.invoiceContent = invoiceContent;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public void setPhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }
}
