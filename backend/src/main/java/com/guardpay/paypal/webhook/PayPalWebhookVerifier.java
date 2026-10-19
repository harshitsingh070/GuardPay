package com.guardpay.paypal.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.config.PayPalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.guardpay.paypal.webhook.dto.PayPalVerifySignatureRequest;
import com.guardpay.paypal.webhook.dto.PayPalVerifySignatureResponse;

/**
 * PayPal webhook signature verification via
 * {@code POST /v1/notifications/verify-webhook-signature}.
 *
 * <p>Fail-closed: missing headers, unconfigured webhook ID, PayPal errors,
 * or any non-{@code SUCCESS} verdict all reject the event. Only safe labels
 * are logged — never signatures, certificates, or payloads.
 */
@Component
public class PayPalWebhookVerifier {

  private static final Logger log = LoggerFactory.getLogger(PayPalWebhookVerifier.class);

  static final String HEADER_TRANSMISSION_ID = "paypal-transmission-id";
  static final String HEADER_TRANSMISSION_TIME = "paypal-transmission-time";
  static final String HEADER_TRANSMISSION_SIG = "paypal-transmission-sig";
  static final String HEADER_CERT_URL = "paypal-cert-url";
  static final String HEADER_AUTH_ALGO = "paypal-auth-algo";

  private final PayPalClient payPalClient;
  private final PayPalProperties properties;

  public PayPalWebhookVerifier(PayPalClient payPalClient, PayPalProperties properties) {
    this.payPalClient = payPalClient;
    this.properties = properties;
  }

  /**
   * Verifies a delivery against PayPal.
   *
   * @throws ResponseStatusException 400 (missing headers / not configured),
   *     401 (invalid signature)
   */
  public void verifyOrThrow(HttpHeaders headers, JsonNode eventNode) {
    String transmissionId = header(headers, HEADER_TRANSMISSION_ID);
    String transmissionTime = header(headers, HEADER_TRANSMISSION_TIME);
    String transmissionSig = header(headers, HEADER_TRANSMISSION_SIG);
    String certUrl = header(headers, HEADER_CERT_URL);
    String authAlgo = header(headers, HEADER_AUTH_ALGO);
    if (transmissionId == null
        || transmissionTime == null
        || transmissionSig == null
        || certUrl == null
        || authAlgo == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing PayPal webhook headers");
    }
    String webhookId = properties.getWebhookId();
    if (webhookId == null || webhookId.isBlank()) {
      log.warn("PayPal webhook rejected: webhook ID is not configured");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayPal webhooks are not configured");
    }

    PayPalVerifySignatureResponse verdict =
        payPalClient.post(
            "/v1/notifications/verify-webhook-signature",
            new PayPalVerifySignatureRequest(
                transmissionId, transmissionTime, certUrl, authAlgo, transmissionSig, webhookId, eventNode),
            PayPalVerifySignatureResponse.class);

    if (verdict == null || !"SUCCESS".equalsIgnoreCase(verdict.verificationStatus())) {
      log.warn("PayPal webhook rejected: invalid signature");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }
  }

  private static String header(HttpHeaders headers, String name) {
    return headers.getFirst(name);
  }
}
