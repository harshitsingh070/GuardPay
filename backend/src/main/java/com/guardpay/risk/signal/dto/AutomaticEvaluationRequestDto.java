package com.guardpay.risk.signal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for {@code POST /api/risk/evaluate-automatic}: payment context
 * only. No risk booleans exist on this path — the backend derives them.
 */
public class AutomaticEvaluationRequestDto {

  @NotNull(message = "amount is required")
  @DecimalMin(value = "0.00", message = "amount must be >= 0")
  private BigDecimal amount;

  private String currency;

  @NotNull(message = "userId is required")
  private UUID userId;

  private UUID recipientId;

  private UUID merchantId;

  private UUID transactionId;

  /**
   * Optional device identity for trusted call-state lookup. There is
   * deliberately no {@code activeCall} field — the backend resolves device
   * state itself and a client boolean could never override it.
   */
  private UUID deviceId;

  /** Optional message/invoice text for AI content analysis (absent = not analyzed). */
  @Size(max = 4000, message = "contentText must be 4000 characters or fewer")
  private String contentText;

  /** Optional payment URL for AI structural analysis (absent = not analyzed). */
  @Size(max = 2048, message = "contentUrl must be 2048 characters or fewer")
  private String contentUrl;

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

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getRecipientId() {
    return recipientId;
  }

  public void setRecipientId(UUID recipientId) {
    this.recipientId = recipientId;
  }

  public UUID getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(UUID merchantId) {
    this.merchantId = merchantId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(UUID transactionId) {
    this.transactionId = transactionId;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(UUID deviceId) {
    this.deviceId = deviceId;
  }

  public String getContentText() {
    return contentText;
  }

  public void setContentText(String contentText) {
    this.contentText = contentText;
  }

  public String getContentUrl() {
    return contentUrl;
  }

  public void setContentUrl(String contentUrl) {
    this.contentUrl = contentUrl;
  }
}
