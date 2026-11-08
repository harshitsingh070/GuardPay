package com.guardpay.paypal.service;

import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.alert.AlertService;
import com.guardpay.behavior.BehavioralOutcomeService;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.dto.PayPalAmount;
import com.guardpay.paypal.dto.PayPalCaptureResponse;
import com.guardpay.paypal.dto.PayPalCreateOrderRequest;
import com.guardpay.paypal.dto.PayPalCreateOrderResponse;
import com.guardpay.paypal.dto.PayPalPayee;
import com.guardpay.paypal.dto.PayPalPurchaseUnit;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * PayPal Orders flow with a server-side GuardPay risk gate.
 *
 * <p>Order creation is only reachable through the guarded-payment
 * orchestrator: there is no public, client-supplied risk bypass. Capture:
 * reload the stored decision by PayPal order ID and call PayPal capture ONLY
 * for ALLOW. The frontend can never force a decision — it supplies context,
 * never verdicts.
 */
@Service
public class PayPalOrderService {

  private static final Logger log = LoggerFactory.getLogger(PayPalOrderService.class);

  static final String SUPPORTED_CURRENCY = "USD";

  private final PayPalClient payPalClient;
  private final TransactionRepository transactions;
  private final RiskAssessmentRepository assessments;
  private final PaymentVerificationRepository verifications;
  private final AlertService alertService;
  private final BehavioralOutcomeService behavioralOutcomes;

  /** Legacy/test constructor: large-capture alerts are not recorded. */
  public PayPalOrderService(
      PayPalClient payPalClient,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications) {
    this(payPalClient, transactions, assessments, verifications, null, null);
  }

  public PayPalOrderService(
      PayPalClient payPalClient,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications,
      AlertService alertService) {
    this(
        payPalClient,
        transactions,
        assessments,
        verifications,
        alertService,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PayPalOrderService(
      PayPalClient payPalClient,
      TransactionRepository transactions,
      RiskAssessmentRepository assessments,
      PaymentVerificationRepository verifications,
      AlertService alertService,
      BehavioralOutcomeService behavioralOutcomes) {
    this.payPalClient = payPalClient;
    this.transactions = transactions;
    this.assessments = assessments;
    this.verifications = verifications;
    this.alertService = alertService;
    this.behavioralOutcomes = behavioralOutcomes;
  }

  /**
   * Creates a PayPal order for an already risk-cleared payment (moves no
   * money). Only the guarded-payment orchestrator calls this.
   */
  public CreatedPaypalOrder createPaypalOrder(
      BigDecimal amount, String description, String recipientEmail, String paypalAccountId) {
    if ((recipientEmail == null || recipientEmail.isBlank())
        && (paypalAccountId == null || paypalAccountId.isBlank())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A verified PayPal destination is required");
    }
    // PayPal requires at most two decimals for USD. Amounts reloaded from the
    // database (transactions.amount is NUMERIC(19,4)) would otherwise arrive as
    // e.g. "45.0000" and be rejected with DECIMAL_PRECISION, so normalize here.
    BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
    PayPalPayee payee =
        new PayPalPayee(blankToNull(recipientEmail), blankToNull(paypalAccountId));
    PayPalCreateOrderRequest paypalRequest =
        new PayPalCreateOrderRequest(
            "CAPTURE",
            List.of(
                new PayPalPurchaseUnit(
                    new PayPalAmount(SUPPORTED_CURRENCY, normalized.toPlainString()),
                    description,
                    payee)));
    PayPalCreateOrderResponse created =
        payPalClient.post("/v2/checkout/orders", paypalRequest, PayPalCreateOrderResponse.class);
    verifyDestination(created, payee);
    return new CreatedPaypalOrder(created.id(), created.status(), extractApprovalUrl(created));
  }

  /**
   * Reads the approve link for an existing order. Used only to recover replay
   * data for a row created before the approval URL was persisted; it never
   * creates a second order.
   */
  public String fetchApprovalUrl(String orderId) {
    PayPalCreateOrderResponse order =
        payPalClient.get("/v2/checkout/orders/" + orderId, PayPalCreateOrderResponse.class);
    return extractApprovalUrl(order);
  }

  @Transactional
  public CaptureOrderResponseDto captureOrder(String orderId) {
    return captureOrder(orderId, null);
  }

  @Transactional
  public CaptureOrderResponseDto captureOrderForUser(String orderId, java.util.UUID userId) {
    return captureOrder(orderId, userId);
  }

  private CaptureOrderResponseDto captureOrder(String orderId, java.util.UUID expectedUserId) {
    if (orderId == null || !orderId.matches("[A-Za-z0-9-]+")) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown PayPal order");
    }
    Transaction transaction =
        transactions
            .findByPaypalOrderIdForUpdate(orderId)
            .filter(
                candidate ->
                    expectedUserId == null
                        || (candidate.getUser() != null
                            && expectedUserId.equals(candidate.getUser().getId())))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown PayPal order"));

    // Idempotent replay: a previous capture already terminally settled this
    // order — return the stored result without calling PayPal again. The row
    // lock above serializes simultaneous attempts.
    if (transaction.getPaypalCaptureId() != null) {
      return new CaptureOrderResponseDto(
          orderId,
          transaction.getStatus().name(),
          transaction.getPaypalCaptureId(),
          transaction.getAmount(),
          transaction.getCurrency(),
          GuardedPaymentStatus.from(transaction.getStatus()));
    }

    RiskAssessment decision =
        assessments.findByTransactionOrderByCreatedAtDesc(transaction).stream()
            .findFirst()
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No risk decision for this order"));

    if (decision.getRecommendedAction() != RecommendedAction.ALLOW) {
      // A VERIFY decision clears once its controlled challenge is satisfied —
      // the runner-up order is only ever created after that, but the stored
      // risk decision stays VERIFY, so the gate must consult the verification.
      boolean clearedByVerification =
          decision.getRecommendedAction() == RecommendedAction.VERIFY
              && verifications
                  .findByTransaction(transaction)
                  .map(verification -> verification.getStatus() == VerificationStatus.VERIFIED)
                  .orElse(false);
      if (!clearedByVerification) {
        throw new CaptureBlockedException(
            decision.getRecommendedAction() == RecommendedAction.STOP
                ? "GuardPay stopped this payment before capture."
                : "Additional verification is required before this payment can be captured.",
            decision.getRecommendedAction(),
            decision.getRiskLevel(),
            orderId);
      }
    }

    PayPalCaptureResponse captured =
        payPalClient.post(
            "/v2/checkout/orders/" + orderId + "/capture", Map.of(), PayPalCaptureResponse.class);
    String captureId = extractCaptureId(captured);

    transaction.setPaypalCaptureId(captureId);
    transaction.setStatus(
        "COMPLETED".equalsIgnoreCase(captured.status())
            ? TransactionStatus.COMPLETED
            : TransactionStatus.FAILED);
    transactions.save(transaction);
    if (behavioralOutcomes != null) {
      behavioralOutcomes.recordAfterCommit(transaction, "PAYPAL_CAPTURE");
    }
    if (alertService != null) {
      alertService.recordLargeCapture(transaction);
    }
    log.info("PayPal Sandbox order captured (order linked to GuardPay transaction)");

    return new CaptureOrderResponseDto(
        orderId,
        captured.status(),
        captureId,
        transaction.getAmount(),
        transaction.getCurrency(),
        GuardedPaymentStatus.from(transaction.getStatus()));
  }

  private static void verifyDestination(PayPalCreateOrderResponse created, PayPalPayee expected) {
    if (created == null || created.purchaseUnits() == null) {
      return;
    }
    PayPalPayee returned =
        created.purchaseUnits().stream()
            .filter(unit -> unit != null && unit.payee() != null)
            .map(PayPalCreateOrderResponse.PurchaseUnit::payee)
            .findFirst()
            .orElse(null);
    if (returned == null) {
      return;
    }
    if (expected.emailAddress() != null
        && returned.emailAddress() != null
        && !expected.emailAddress().equalsIgnoreCase(returned.emailAddress())) {
      throw new PayPalException("PayPal returned a different payee than requested");
    }
    if (expected.merchantId() != null
        && returned.merchantId() != null
        && !expected.merchantId().equals(returned.merchantId())) {
      throw new PayPalException("PayPal returned a different merchant than requested");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String extractApprovalUrl(PayPalCreateOrderResponse created) {
    if (created == null || created.links() == null) {
      throw new PayPalException("PayPal did not return an approval URL");
    }
    return created.links().stream()
        .filter(link -> "approve".equalsIgnoreCase(link.rel()))
        .map(link -> link.href())
        .filter(href -> href != null && !href.isBlank())
        .findFirst()
        .orElseThrow(() -> new PayPalException("PayPal did not return an approval URL"));
  }

  private String extractCaptureId(PayPalCaptureResponse captured) {
    if (captured == null || captured.purchaseUnits() == null) {
      throw new PayPalException("PayPal returned an unexpected capture response");
    }
    return captured.purchaseUnits().stream()
        .filter(unit -> unit != null && unit.payments() != null && unit.payments().captures() != null)
        .flatMap(unit -> unit.payments().captures().stream())
        .filter(capture -> capture != null && capture.id() != null && !capture.id().isBlank())
        .map(capture -> capture.id())
        .findFirst()
        .orElseThrow(() -> new PayPalException("PayPal returned an unexpected capture response"));
  }
}
