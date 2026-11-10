package com.guardpay.paypal.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.behavior.BehavioralOutcome;
import com.guardpay.behavior.BehavioralOutcomeService;
import com.guardpay.intelligence.service.RecipientIntelligenceOutcomeService;
import com.guardpay.paypal.webhook.dto.PayPalWebhookEventDto;
import com.guardpay.paypal.webhook.dto.WebhookAckResponse;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * PayPal webhook intake: parse → idempotency → verify → route → sync.
 *
 * <p>Updates GuardPay <em>PayPal state</em> ({@link TransactionStatus}) from
 * what PayPal reports. The GuardPay <em>risk decision</em> lives only in
 * {@code risk_assessments} — this service has no reference to risk classes
 * and can never change ALLOW/VERIFY/STOP. Unknown orders never create
 * transactions; unknown event types are acknowledged without effect.
 */
@Service
public class PayPalWebhookService {

  private static final Logger log = LoggerFactory.getLogger(PayPalWebhookService.class);

  private final PayPalWebhookVerifier verifier;
  private final PayPalWebhookEventRepository events;
  private final TransactionRepository transactions;
  private final ObjectMapper objectMapper;
  private final BehavioralOutcomeService behavioralOutcomes;
  private final RecipientIntelligenceOutcomeService intelligenceOutcomes;

  public PayPalWebhookService(
      PayPalWebhookVerifier verifier,
      PayPalWebhookEventRepository events,
      TransactionRepository transactions,
      ObjectMapper objectMapper) {
    this(verifier, events, transactions, objectMapper, null, null);
  }

  /** Test constructor: recipient intelligence outcomes are not recorded. */
  public PayPalWebhookService(
      PayPalWebhookVerifier verifier,
      PayPalWebhookEventRepository events,
      TransactionRepository transactions,
      ObjectMapper objectMapper,
      BehavioralOutcomeService behavioralOutcomes) {
    this(verifier, events, transactions, objectMapper, behavioralOutcomes, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PayPalWebhookService(
      PayPalWebhookVerifier verifier,
      PayPalWebhookEventRepository events,
      TransactionRepository transactions,
      ObjectMapper objectMapper,
      BehavioralOutcomeService behavioralOutcomes,
      RecipientIntelligenceOutcomeService intelligenceOutcomes) {
    this.verifier = verifier;
    this.events = events;
    this.transactions = transactions;
    this.objectMapper = objectMapper;
    this.behavioralOutcomes = behavioralOutcomes;
    this.intelligenceOutcomes = intelligenceOutcomes;
  }

  @Transactional
  public WebhookAckResponse handleEvent(HttpHeaders headers, String rawBody) {
    JsonNode root = parseBody(rawBody);
    PayPalWebhookEventDto event_ = parseEvent(root);
    String eventId = event_.id();
    String eventType = event_.eventType();
    if (eventId == null || eventId.isBlank() || eventType == null || eventType.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PayPal webhook event");
    }
    log.info("PayPal webhook received (type={})", eventType);

    if (events.findByPaypalEventId(eventId).isPresent()) {
      log.info("PayPal webhook duplicate acknowledged");
      return WebhookAckResponse.ackDuplicate();
    }

    verifier.verifyOrThrow(headers, root);

    PayPalWebhookEvent event = new PayPalWebhookEvent();
    event.setPaypalEventId(eventId);
    event.setEventType(eventType);
    event.setStatus(WebhookProcessingStatus.RECEIVED);
    try {
      events.save(event);
    } catch (DataIntegrityViolationException concurrentDuplicate) {
      // Lost a delivery race: the other transaction owns this event.
      log.info("PayPal webhook duplicate acknowledged");
      return WebhookAckResponse.ackDuplicate();
    }

    try {
      WebhookProcessingStatus outcome = route(eventType, event_.resource());
      event.setStatus(outcome);
      event.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
      events.save(event);
    } catch (RuntimeException failure) {
      event.setStatus(WebhookProcessingStatus.FAILED);
      events.save(event);
      throw failure;
    }
    return WebhookAckResponse.ack();
  }

  private WebhookProcessingStatus route(
      String eventType, PayPalWebhookEventDto.PayPalWebhookResource resource) {
    if (resource == null && requiresResource(eventType)) {
      log.warn("PayPal event without resource ignored");
      return WebhookProcessingStatus.IGNORED;
    }
    return switch (eventType) {
      case "CHECKOUT.ORDER.CREATED" -> syncOrder(resource, TransactionStatus.PENDING);
      case "CHECKOUT.ORDER.APPROVED" -> syncOrder(resource, TransactionStatus.APPROVED);
      case "PAYMENT.CAPTURE.PENDING" -> syncCapture(resource, TransactionStatus.PENDING);
      case "PAYMENT.CAPTURE.COMPLETED" -> syncCapture(resource, TransactionStatus.COMPLETED);
      case "PAYMENT.CAPTURE.DENIED" -> syncCapture(resource, TransactionStatus.FAILED);
      case "PAYMENT.CAPTURE.REFUNDED" ->
          recordExclusion(resource, BehavioralOutcome.REFUNDED);
      case "PAYMENT.CAPTURE.REVERSED" ->
          recordExclusion(resource, BehavioralOutcome.REVERSED);
      case "PAYMENT.CAPTURE.DISPUTED", "CUSTOMER.DISPUTE.CREATED" ->
          recordExclusion(resource, BehavioralOutcome.DISPUTED);
      default -> {
        log.info("Unknown PayPal event type acknowledged without effect (type={})", eventType);
        yield WebhookProcessingStatus.IGNORED;
      }
    };
  }

  private static boolean requiresResource(String eventType) {
    return eventType.startsWith("CHECKOUT.") || eventType.startsWith("PAYMENT.CAPTURE.");
  }

  private WebhookProcessingStatus syncOrder(
      PayPalWebhookEventDto.PayPalWebhookResource resource, TransactionStatus status) {
    String orderId = resource.id();
    if (orderId == null || orderId.isBlank()) {
      log.warn("PayPal order event without identifier ignored");
      return WebhookProcessingStatus.IGNORED;
    }
    Optional<Transaction> transaction = transactions.findByPaypalOrderId(orderId);
    if (transaction.isEmpty()) {
      log.warn("PayPal webhook for unknown order ignored (no transaction created)");
      return WebhookProcessingStatus.IGNORED;
    }
    Transaction current = transaction.get();
    if (!canApply(current.getStatus(), status)) {
      log.info(
          "PayPal webhook would regress order state ({} -> {}); keeping current state",
          current.getStatus(),
          status);
      return WebhookProcessingStatus.PROCESSED;
    }
    current.setStatus(status);
    transactions.save(current);
    return WebhookProcessingStatus.PROCESSED;
  }

  private WebhookProcessingStatus syncCapture(
      PayPalWebhookEventDto.PayPalWebhookResource resource, TransactionStatus status) {
    String captureId = resource.id();
    if (captureId == null || captureId.isBlank()) {
      log.warn("PayPal capture event without identifier ignored");
      return WebhookProcessingStatus.IGNORED;
    }
    Optional<Transaction> transaction = transactions.findByPaypalCaptureId(captureId);
    if (transaction.isEmpty()) {
      String orderId = relatedOrderId(resource.supplementaryData());
      if (orderId != null) {
        transaction = transactions.findByPaypalOrderId(orderId);
      }
    }
    if (transaction.isEmpty()) {
      log.warn("PayPal webhook for unknown capture ignored (no transaction created)");
      return WebhookProcessingStatus.IGNORED;
    }
    Transaction current = transaction.get();
    if (!canApply(current.getStatus(), status)) {
      log.info(
          "PayPal webhook would regress capture state ({} -> {}); keeping current state",
          current.getStatus(),
          status);
      recordBehavioralOutcome(current);
      return WebhookProcessingStatus.PROCESSED;
    }
    if (current.getPaypalCaptureId() == null) {
      current.setPaypalCaptureId(captureId);
    }
    current.setStatus(status);
    transactions.save(current);
    recordBehavioralOutcome(current);
    return WebhookProcessingStatus.PROCESSED;
  }

  /**
   * Refunds, reversals, and disputes do not change the original transaction
   * status (there is no separate status for them), but they must remove the
   * payment from the learned baseline.
   */
  private WebhookProcessingStatus recordExclusion(
      PayPalWebhookEventDto.PayPalWebhookResource resource, BehavioralOutcome outcome) {
    Optional<Transaction> transaction = resolveCaptureTransaction(resource);
    if (transaction.isEmpty()) {
      log.info("PayPal exclusion event for unknown payment ignored (outcome={})", outcome);
      return WebhookProcessingStatus.IGNORED;
    }
    if (behavioralOutcomes != null) {
      behavioralOutcomes.recordExclusionAfterCommit(
          transaction.get(), outcome, "PAYPAL_WEBHOOK:" + outcome.name());
    }
    if (intelligenceOutcomes != null) {
      intelligenceOutcomes.recordExclusionAfterCommit(
          transaction.get(), "PAYPAL_WEBHOOK:" + outcome.name());
    }
    log.info("PayPal exclusion event recorded for behavioral baseline (outcome={})", outcome);
    return WebhookProcessingStatus.PROCESSED;
  }

  private Optional<Transaction> resolveCaptureTransaction(
      PayPalWebhookEventDto.PayPalWebhookResource resource) {
    String captureId = resource.id();
    if (captureId != null && !captureId.isBlank()) {
      Optional<Transaction> byCapture = transactions.findByPaypalCaptureId(captureId);
      if (byCapture.isPresent()) {
        return byCapture;
      }
    }
    String orderId = relatedOrderId(resource.supplementaryData());
    if (orderId != null && !orderId.isBlank()) {
      return transactions.findByPaypalOrderId(orderId);
    }
    if (captureId != null && !captureId.isBlank()) {
      return transactions.findByPaypalOrderId(captureId);
    }
    return Optional.empty();
  }

  private void recordBehavioralOutcome(Transaction transaction) {
    if (behavioralOutcomes == null) {
      return;
    }
    if (transaction.getStatus() == TransactionStatus.COMPLETED
        || transaction.getStatus() == TransactionStatus.FAILED
        || transaction.getStatus() == TransactionStatus.CANCELLED) {
      behavioralOutcomes.recordAfterCommit(transaction, "PAYPAL_WEBHOOK");
    }
  }

  /**
   * PayPal events can arrive out of order. Terminal outcomes are final and a
   * payment never moves backwards: APPROVED cannot return to PENDING, and a
   * FAILED payment only recovers to COMPLETED (for example, a retried capture
   * on the same order).
   */
  private static boolean canApply(TransactionStatus current, TransactionStatus target) {
    if (current == TransactionStatus.COMPLETED || current == TransactionStatus.CANCELLED) {
      return false;
    }
    if (current == TransactionStatus.FAILED) {
      return target == TransactionStatus.COMPLETED;
    }
    return !(current == TransactionStatus.APPROVED && target == TransactionStatus.PENDING);
  }

  /** Best-effort related order id from {@code supplementary_data.related_ids.order_id}. */
  private static String relatedOrderId(JsonNode supplementaryData) {
    if (supplementaryData == null) {
      return null;
    }
    JsonNode orderId = supplementaryData.path("related_ids").path("order_id");
    return orderId.isTextual() ? orderId.asText() : null;
  }

  private JsonNode parseBody(String rawBody) {
    try {
      return objectMapper.readTree(rawBody);
    } catch (JsonProcessingException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PayPal webhook event");
    }
  }

  private PayPalWebhookEventDto parseEvent(JsonNode root) {
    try {
      return objectMapper.treeToValue(root, PayPalWebhookEventDto.class);
    } catch (JsonProcessingException invalid) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PayPal webhook event");
    }
  }
}
