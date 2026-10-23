package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One PayPal purchase unit (single-unit payments for this milestone). */
public record PayPalPurchaseUnit(
    @JsonProperty("amount") PayPalAmount amount,
    @JsonProperty("description") @JsonInclude(JsonInclude.Include.NON_NULL) String description,
    @JsonProperty("payee") PayPalPayee payee) {}
