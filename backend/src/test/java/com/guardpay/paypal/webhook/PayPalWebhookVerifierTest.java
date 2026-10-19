package com.guardpay.paypal.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.webhook.dto.PayPalVerifySignatureRequest;
import com.guardpay.paypal.webhook.dto.PayPalVerifySignatureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Signature verification with a mocked PayPal client. Proves the request
 * carries the webhook ID (never secrets) and verdicts map correctly.
 */
@ExtendWith(MockitoExtension.class)
class PayPalWebhookVerifierTest {

  @Mock private PayPalClient payPalClient;

  private PayPalProperties properties;
  private PayPalWebhookVerifier verifier;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    properties = new PayPalProperties();
    properties.setClientSecret("super-secret-sentinel");
    properties.setWebhookId("WH-TEST-123");
    verifier = new PayPalWebhookVerifier(payPalClient, properties);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.add(PayPalWebhookVerifier.HEADER_TRANSMISSION_ID, "trans-1");
    headers.add(PayPalWebhookVerifier.HEADER_TRANSMISSION_TIME, "2026-10-06T10:00:00Z");
    headers.add(PayPalWebhookVerifier.HEADER_TRANSMISSION_SIG, "sig-abc");
    headers.add(PayPalWebhookVerifier.HEADER_CERT_URL, "https://api.sandbox.paypal.com/certs");
    headers.add(PayPalWebhookVerifier.HEADER_AUTH_ALGO, "SHA256withRSA");
    return headers;
  }

  private JsonNode eventNode() throws Exception {
    return objectMapper.readTree("{\"id\": \"EVT-1\", \"event_type\": \"CHECKOUT.ORDER.APPROVED\"}");
  }

  @Test
  void successVerdictPassesWithSafeRequest() throws Exception {
    when(payPalClient.post(
            eq("/v1/notifications/verify-webhook-signature"),
            any(PayPalVerifySignatureRequest.class),
            eq(PayPalVerifySignatureResponse.class)))
        .thenReturn(new PayPalVerifySignatureResponse("SUCCESS"));

    verifier.verifyOrThrow(headers(), eventNode());

    ArgumentCaptor<PayPalVerifySignatureRequest> sent =
        ArgumentCaptor.forClass(PayPalVerifySignatureRequest.class);
    verify(payPalClient)
        .post(
            eq("/v1/notifications/verify-webhook-signature"),
            sent.capture(),
            eq(PayPalVerifySignatureResponse.class));
    assertEquals("WH-TEST-123", sent.getValue().webhookId());
    assertEquals("sig-abc", sent.getValue().transmissionSig());
    String serialized = objectMapper.writeValueAsString(sent.getValue());
    assertTrue(serialized.contains("WH-TEST-123"));
    assertFalse(serialized.contains("super-secret-sentinel"));
  }

  @Test
  void failureVerdictIsUnauthorized() throws Exception {
    when(payPalClient.post(any(), any(), any(Class.class)))
        .thenReturn(new PayPalVerifySignatureResponse("FAILURE"));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> verifier.verifyOrThrow(headers(), eventNode()));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
  }

  @Test
  void missingHeadersAreRejectedWithoutPayPalCall() throws Exception {
    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> verifier.verifyOrThrow(new HttpHeaders(), eventNode()));

    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    verify(payPalClient, never()).post(any(), any(), any(Class.class));
  }

  @Test
  void missingWebhookIdIsRejectedWithoutPayPalCall() throws Exception {
    properties.setWebhookId(" ");

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> verifier.verifyOrThrow(headers(), eventNode()));

    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    verify(payPalClient, never()).post(any(), any(), any(Class.class));
  }
}
