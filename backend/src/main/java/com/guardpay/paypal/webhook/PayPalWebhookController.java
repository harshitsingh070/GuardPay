package com.guardpay.paypal.webhook;

import com.guardpay.paypal.webhook.dto.WebhookAckResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PayPal webhook receiver (development stage — called by PayPal itself, so no
 * user authentication by design; signature verification is the trust
 * mechanism). Thin by design: receives the raw body and delegates.
 */
@RestController
@RequestMapping("/api/paypal/webhooks")
public class PayPalWebhookController {

  private final PayPalWebhookService service;

  public PayPalWebhookController(PayPalWebhookService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<WebhookAckResponse> receive(
      @RequestHeader HttpHeaders headers, @RequestBody String rawBody) {
    return ResponseEntity.ok(service.handleEvent(headers, rawBody));
  }
}
