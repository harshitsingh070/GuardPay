package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** PayPal {@code POST /v2/checkout/orders} response (subset we use). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalCreateOrderResponse(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("links") List<PayPalLink> links,
    @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PurchaseUnit(@JsonProperty("payee") PayPalPayee payee) {}
}
