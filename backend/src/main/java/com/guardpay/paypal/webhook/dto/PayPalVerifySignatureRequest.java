package com.guardpay.paypal.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Body for {@code POST /v1/notifications/verify-webhook-signature}.
 * Carries transmission metadata plus the complete event — never credentials
 * (authentication rides the bearer token in {@code PayPalClient}).
 */
public record PayPalVerifySignatureRequest(
    @JsonProperty("transmission_id") String transmissionId,
    @JsonProperty("transmission_time") String transmissionTime,
    @JsonProperty("cert_url") String certUrl,
    @JsonProperty("auth_algo") String authAlgo,
    @JsonProperty("transmission_sig") String transmissionSig,
    @JsonProperty("webhook_id") String webhookId,
    @JsonProperty("webhook_event") JsonNode webhookEvent) {}
