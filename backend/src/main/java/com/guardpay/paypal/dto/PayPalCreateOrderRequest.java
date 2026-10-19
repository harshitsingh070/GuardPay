package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Body for {@code POST /v2/checkout/orders}. Amount comes from the GuardPay request. */
public record PayPalCreateOrderRequest(
    @JsonProperty("intent") String intent,
    @JsonProperty("purchase_units") List<PayPalPurchaseUnit> purchaseUnits) {}
