package com.guardpay.paypal.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Safe webhook acknowledgement: receipt flags only, no PayPal internals. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebhookAckResponse(boolean received, Boolean duplicate) {
  public static WebhookAckResponse ack() {
    return new WebhookAckResponse(true, null);
  }

  public static WebhookAckResponse ackDuplicate() {
    return new WebhookAckResponse(true, true);
  }
}
