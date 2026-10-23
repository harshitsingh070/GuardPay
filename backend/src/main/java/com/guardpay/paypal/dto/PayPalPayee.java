package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Explicit PayPal destination. One of email or merchant id is required. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalPayee(
    @JsonProperty("email_address") @JsonInclude(JsonInclude.Include.NON_NULL)
        String emailAddress,
    @JsonProperty("merchant_id") @JsonInclude(JsonInclude.Include.NON_NULL)
        String merchantId) {}
