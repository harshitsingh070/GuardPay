package com.guardpay.paypal.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * PayPal {@code POST /v2/checkout/orders/{id}/capture} response (subset we
 * use): order id/status plus the first capture's id/status.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PayPalCaptureResponse(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("purchase_units") List<CaptureUnit> purchaseUnits) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CaptureUnit(@JsonProperty("payments") CapturePayments payments) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CapturePayments(@JsonProperty("captures") List<Capture> captures) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Capture(
      @JsonProperty("id") String id,
      @JsonProperty("status") String status,
      @JsonProperty("amount") PayPalAmount amount) {}
}
