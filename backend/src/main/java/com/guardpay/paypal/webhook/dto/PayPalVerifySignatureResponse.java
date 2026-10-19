package com.guardpay.paypal.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** PayPal signature-verification verdict ({@code SUCCESS} or anything else). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalVerifySignatureResponse(
    @JsonProperty("verification_status") String verificationStatus) {}
