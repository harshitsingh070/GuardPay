package com.guardpay.payment.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for {@code POST /api/payments/guarded}: payment/business context
 * only. There are deliberately NO risk fields — no score, level, action,
 * booleans, or AI findings. The backend derives every signal.
 */
public class GuardedPaymentRequestDto {

  @NotNull(message = "amount is required")
  @DecimalMin(value = "0.01", message = "amount must be greater than 0")
  @DecimalMax(value = "100000.00", message = "amount exceeds the maximum")
  private BigDecimal amount;

  @NotBlank(message = "currency is required")
  @Pattern(regexp = "USD", message = "currency must be USD for this milestone")
  private String currency;

  @Size(max = 255, message = "description must be 255 characters or fewer")
  private String description;

  /** Optional known user; the demo placeholder applies when absent (no auth yet). */
  private UUID userId;

  /** Optional known recipient; {@code recipientName} is resolved/learned otherwise. */
  private UUID recipientId;

  @Size(max = 255, message = "recipientName must be 255 characters or fewer")
  private String recipientName;

  /** Optional known merchant; {@code merchantName} is resolved/learned otherwise. */
  private UUID merchantId;

  @Size(max = 255, message = "merchantName must be 255 characters or fewer")
  private String merchantName;

  /** Optional paired-device identity for trusted call-state lookup. */
  private UUID deviceId;

  @Size(max = 4000, message = "contentText must be 4000 characters or fewer")
  private String contentText;

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public String getRecipientName() {
    return recipientName;
  }

  public void setRecipientName(String recipientName) {
    this.recipientName = recipientName;
  }

  public UUID getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(UUID merchantId) {
    this.merchantId = merchantId;
  }

  public String getMerchantName() {
    return merchantName;
  }

  public void setMerchantName(String merchantName) {
    this.merchantName = merchantName;
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
