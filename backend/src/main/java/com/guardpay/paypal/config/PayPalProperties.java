package com.guardpay.paypal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PayPal connection settings. All values come from environment variables —
 * credentials are never hard-coded, never logged, and never exposed.
 *
 * <p>Validation is intentionally lazy (see {@code PayPalOAuthService}):
 * the application boots without PayPal credentials so unrelated features
 * keep working; PayPal calls fail clearly at use time instead.
 */
@ConfigurationProperties(prefix = "guardpay.paypal")
public class PayPalProperties {

  /** Sandbox default; override with {@code PAYPAL_BASE_URL} for live. */
  private String baseUrl = "https://api-m.sandbox.paypal.com";

  /** From {@code PAYPAL_CLIENT_ID}. */
  private String clientId;

  /** From {@code PAYPAL_CLIENT_SECRET}. Backend only — never leaves the server. */
  private String clientSecret;

  /**
   * From {@code PAYPAL_WEBHOOK_ID} (PayPal Developer Dashboard). Used only
   * for webhook signature verification; never logged.
   */
  private String webhookId;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getWebhookId() {
    return webhookId;
  }

  public void setWebhookId(String webhookId) {
    this.webhookId = webhookId;
  }

  /** {@code SANDBOX} for sandbox URLs, {@code LIVE} otherwise (used in health output). */
  public String environmentLabel() {
    return baseUrl != null && baseUrl.contains("sandbox") ? "SANDBOX" : "LIVE";
  }
}
