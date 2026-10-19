package com.guardpay.paypal.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal inbound PayPal webhook shape: identity + routing info only.
 * Unknown payload fields are ignored; full payloads are never persisted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalWebhookEventDto(
    @JsonProperty("id") String id,
    @JsonProperty("event_type") String eventType,
    @JsonProperty("create_time") String createTime,
    @JsonProperty("resource") PayPalWebhookResource resource) {

  /**
   * The {@code resource} node: order/capture id + status, plus an opaque
   * {@code supplementary_data} subtree for related identifiers.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PayPalWebhookResource(
      @JsonProperty("id") String id,
      @JsonProperty("status") String status,
      @JsonProperty("supplementary_data") JsonNode supplementaryData) {}
}
