package com.guardpay.paypal.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.paypal.webhook.dto.WebhookAckResponse;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import java.util.List;
import java.util.Optional;
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
 * Webhook intake with mocked verifier and repositories — no network, no
 * database. The risk decision is structurally unreachable here (see
 * {@code WebhookRiskSeparationTest}); these tests prove state sync only.
 */
@ExtendWith(MockitoExtension.class)
class PayPalWebhookServiceTest {

  @Mock private PayPalWebhookVerifier verifier;
  @Mock private PayPalWebhookEventRepository events;
  @Mock private TransactionRepository transactions;

  private PayPalWebhookService service;
  private final HttpHeaders headers = new HttpHeaders();

  @BeforeEach
  void setUp() {
    service = new PayPalWebhookService(verifier, events, transactions, new ObjectMapper());
  }

  private static String eventJson(String eventId, String type, String resourceJson) {
    return "{\"id\": \""
        + eventId
        + "\", \"event_type\": \""
        + type
        + "\", \"create_time\": \"2026-10-06T10:00:00Z\", \"resource\": "
        + resourceJson
        + "}";
  }

  private void stubSaveEcho() {
    when(events.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private PayPalWebhookEvent lastSavedEvent() {
    ArgumentCaptor<PayPalWebhookEvent> saved = ArgumentCaptor.forClass(PayPalWebhookEvent.class);
    verify(events, org.mockito.Mockito.times(2)).save(saved.capture());
    List<PayPalWebhookEvent> all = saved.getAllValues();
    return all.get(all.size() - 1);
  }

  private static Transaction orderTransaction() {
    Transaction transaction = new Transaction();
    transaction.setStatus(TransactionStatus.PENDING);
    return transaction;
  }

  @Test
  void approvedOrderUpdatesTransaction() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-1")).thenReturn(Optional.empty());
    Transaction transaction = orderTransaction();
    when(transactions.findByPaypalOrderId("ORDER-1")).thenReturn(Optional.of(transaction));

    WebhookAckResponse ack =
        service.handleEvent(
            headers, eventJson("EVT-1", "CHECKOUT.ORDER.APPROVED", "{\"id\": \"ORDER-1\"}"));

    assertTrue(ack.received());
    assertNull(ack.duplicate());
    assertEquals(TransactionStatus.PENDING, transaction.getStatus());
    assertEquals(WebhookProcessingStatus.PROCESSED, lastSavedEvent().getStatus());
  }

  @Test
  void createdOrderUpdatesTransaction() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-2")).thenReturn(Optional.empty());
    Transaction transaction = orderTransaction();
    transaction.setStatus(TransactionStatus.PENDING);
    when(transactions.findByPaypalOrderId("ORDER-2")).thenReturn(Optional.of(transaction));

    service.handleEvent(headers, eventJson("EVT-2", "CHECKOUT.ORDER.CREATED", "{\"id\": \"ORDER-2\"}"));

    assertEquals(TransactionStatus.PENDING, transaction.getStatus());
    assertEquals(WebhookProcessingStatus.PROCESSED, lastSavedEvent().getStatus());
  }

  @Test
  void invalidSignatureRejectsWithoutPersistence() {
    when(events.findByPaypalEventId("EVT-9")).thenReturn(Optional.empty());
    doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature"))
        .when(verifier)
        .verifyOrThrow(any(), any());

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class,
            () ->
                service.handleEvent(
                    headers, eventJson("EVT-9", "CHECKOUT.ORDER.APPROVED", "{\"id\": \"ORDER-9\"}")));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(events, never()).save(any());
    verify(transactions, never()).save(any());
  }

  @Test
  void duplicateIsAcknowledgedWithoutReprocessing() {
    PayPalWebhookEvent seen = new PayPalWebhookEvent();
    seen.setStatus(WebhookProcessingStatus.PROCESSED);
    when(events.findByPaypalEventId("EVT-1")).thenReturn(Optional.of(seen));

    WebhookAckResponse ack =
        service.handleEvent(
            headers, eventJson("EVT-1", "CHECKOUT.ORDER.APPROVED", "{\"id\": \"ORDER-1\"}"));

    assertTrue(ack.received());
    assertTrue(ack.duplicate());
    verify(verifier, org.mockito.Mockito.never()).verifyOrThrow(any(), any());
    verify(events, never()).save(any());
    verify(transactions, never()).save(any());
  }

  @Test
  void captureCompletedViaCaptureId() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-3")).thenReturn(Optional.empty());
    Transaction transaction = orderTransaction();
    when(transactions.findByPaypalCaptureId("CAP-1")).thenReturn(Optional.of(transaction));

    service.handleEvent(
        headers,
        eventJson("EVT-3", "PAYMENT.CAPTURE.COMPLETED", "{\"id\": \"CAP-1\", \"status\": \"COMPLETED\"}"));

    assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    assertEquals("CAP-1", transaction.getPaypalCaptureId());
    assertEquals(WebhookProcessingStatus.PROCESSED, lastSavedEvent().getStatus());
  }

  @Test
  void captureCompletedViaRelatedOrderId() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-4")).thenReturn(Optional.empty());
    Transaction transaction = orderTransaction();
    transaction.setPaypalOrderId("ORDER-4");
    when(transactions.findByPaypalCaptureId("CAP-4")).thenReturn(Optional.empty());
    when(transactions.findByPaypalOrderId("ORDER-4")).thenReturn(Optional.of(transaction));

    service.handleEvent(
        headers,
        eventJson(
            "EVT-4",
            "PAYMENT.CAPTURE.COMPLETED",
            "{\"id\": \"CAP-4\", \"supplementary_data\": {\"related_ids\": {\"order_id\": \"ORDER-4\"}}}"));

    assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
    assertEquals("CAP-4", transaction.getPaypalCaptureId());
  }

  @Test
  void captureDeniedMapsFailed() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-5")).thenReturn(Optional.empty());
    Transaction transaction = orderTransaction();
    when(transactions.findByPaypalCaptureId("CAP-5")).thenReturn(Optional.of(transaction));

    service.handleEvent(
        headers,
        eventJson("EVT-5", "PAYMENT.CAPTURE.DENIED", "{\"id\": \"CAP-5\", \"status\": \"DENIED\"}"));

    assertEquals(TransactionStatus.FAILED, transaction.getStatus());
  }

  @Test
  void unknownEventTypeIsAcknowledgedWithoutEffect() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-6")).thenReturn(Optional.empty());

    WebhookAckResponse ack =
        service.handleEvent(headers, eventJson("EVT-6", "BILLING.SUBSCRIPTION.CREATED", "{}"));

    assertTrue(ack.received());
    assertEquals(WebhookProcessingStatus.IGNORED, lastSavedEvent().getStatus());
    verify(transactions, never()).save(any());
  }

  @Test
  void unknownOrderCreatesNoTransaction() {
    stubSaveEcho();
    when(events.findByPaypalEventId("EVT-7")).thenReturn(Optional.empty());
    when(transactions.findByPaypalOrderId("ORDER-UNKNOWN")).thenReturn(Optional.empty());

    WebhookAckResponse ack =
        service.handleEvent(
            headers, eventJson("EVT-7", "CHECKOUT.ORDER.APPROVED", "{\"id\": \"ORDER-UNKNOWN\"}"));

    assertTrue(ack.received());
    assertEquals(WebhookProcessingStatus.IGNORED, lastSavedEvent().getStatus());
    verify(transactions, never()).save(any());
  }

  @Test
  void malformedBodyIsRejected() {
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.handleEvent(headers, "{oops"));

    assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    verify(events, never()).save(any());
  }
}
