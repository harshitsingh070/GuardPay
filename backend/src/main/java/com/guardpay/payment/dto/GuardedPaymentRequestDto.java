package com.guardpay.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for {@code POST /api/payments/guarded}: payment/business context
 * only. There are deliberately NO risk fields â€” no score, level, action,
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

  /** Optional known recipient; {@code recipientName} is resolved/learned otherwise. */
  private UUID recipientId;

  @Size(max = 255, message = "recipientName must be 255 characters or fewer")
  private String recipientName;

  /** Verified PayPal destination for the recipient; required before order creation. */
  @Email(message = "recipientEmail must be a valid email")
  @Size(max = 255, message = "recipientEmail must be 255 characters or fewer")
  private String recipientEmail;

  /** Alternative PayPal merchant/account id when the recipient has no email. */
  @Size(max = 128, message = "recipientPaypalAccountId must be 128 characters or fewer")
  private String recipientPaypalAccountId;

  /** Optional known merchant; {@code merchantName} is resolved/learned otherwise. */
  private UUID merchantId;

  @Size(max = 255, message = "merchantName must be 255 characters or fewer")
  private String merchantName;

  /** Optional paired-device identity for trusted call-state lookup. */
  private UUID deviceId;

  /** Stable client device fingerprint; treated as a device hint, never a verdict. */
  @Size(max = 128, message = "deviceFingerprint must be 128 characters or fewer")
  private String deviceFingerprint;

  /**
   * Server-observed network context. These fields are populated by the
   * controller (IP) or a trusted resolver, so JSON cannot forge them.
   */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 64, message = "clientIp must be 64 characters or fewer")
  private String clientIp;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 2, message = "geoCountry must be a 2-letter country code")
  private String geoCountry;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 128, message = "geoCity must be 128 characters or fewer")
  private String geoCity;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer asn;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Boolean vpnOrProxy;

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

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public void setRecipientEmail(String recipientEmail) {
    this.recipientEmail = recipientEmail;
  }

  public String getRecipientPaypalAccountId() {
    return recipientPaypalAccountId;
  }

  public void setRecipientPaypalAccountId(String recipientPaypalAccountId) {
    this.recipientPaypalAccountId = recipientPaypalAccountId;
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

  public String getDeviceFingerprint() {
    return deviceFingerprint;
  }

  public void setDeviceFingerprint(String deviceFingerprint) {
    this.deviceFingerprint = deviceFingerprint;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  public String getGeoCountry() {
    return geoCountry;
  }

  public void setGeoCountry(String geoCountry) {
    this.geoCountry = geoCountry;
  }

  public String getGeoCity() {
    return geoCity;
  }

  public void setGeoCity(String geoCity) {
    this.geoCity = geoCity;
  }

  public Integer getAsn() {
    return asn;
  }

  public void setAsn(Integer asn) {
    this.asn = asn;
  }

  public Boolean getVpnOrProxy() {
    return vpnOrProxy;
  }

  public void setVpnOrProxy(Boolean vpnOrProxy) {
    this.vpnOrProxy = vpnOrProxy;
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
