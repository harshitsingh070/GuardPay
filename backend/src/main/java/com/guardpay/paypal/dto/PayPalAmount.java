package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code amount} node inside a PayPal purchase unit. Value is a 2-decimal string. */
public record PayPalAmount(
    @JsonProperty("currency_code") String currencyCode, @JsonProperty("value") String value) {}
