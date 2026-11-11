package com.guardpay.risk.signal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request for {@code POST /api/risk/evaluate-automatic}: payment context
 * only. No risk booleans exist on this path â€” the backend derives them.
 */
public class AutomaticEvaluationRequestDto {

  @NotNull(message = "amount is required")
  @DecimalMin(value = "0.00", message = "amount must be >= 0")
  private BigDecimal amount;

  private String currency;

  private UUID recipientId;

  private UUID merchantId;

  private UUID transactionId;

  /**
   * Optional device identity for trusted call-state lookup. There is
   * deliberately no {@code activeCall} field â€” the backend resolves device
   * state itself and a client boolean could never override it.
   */
  private UUID deviceId;

  /** Stable client device fingerprint; a second device identity for history. */
  @Size(max = 128, message = "deviceFingerprint must be 128 characters or fewer")
  private String deviceFingerprint;

  /** Server-observed client IP; populated by the controller, never trusted from JSON. */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 64, message = "clientIp must be 64 characters or fewer")
  private String clientIp;

  /** Country resolved from the client network context (ISO-3166 alpha-2). */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 2, message = "geoCountry must be a 2-letter country code")
  private String geoCountry;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Size(max = 128, message = "geoCity must be 128 characters or fewer")
  private String geoCity;

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Integer asn;

  /** Network reputation result: true when the connection is a VPN/proxy. */
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  private Boolean vpnOrProxy;

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
