package com.guardpay.paypal.service;

import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.payment.PaymentContext;
import com.guardpay.payment.PaymentContextRepository;
import com.guardpay.payment.PaymentVerificationRepository;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.paypal.client.PayPalClient;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.CreateOrderRequestDto;
import com.guardpay.paypal.dto.CreateOrderResponseDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.dto.PayPalAmount;
import com.guardpay.paypal.dto.PayPalCaptureResponse;
import com.guardpay.paypal.dto.PayPalCreateOrderRequest;
import com.guardpay.paypal.dto.PayPalCreateOrderResponse;
import com.guardpay.paypal.dto.PayPalPurchaseUnit;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.RiskEvaluationRequest;
import com.guardpay.risk.RiskEvaluationResult;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * <p>Order: validate → run engine → persist GuardPay records → create the
 * PayPal order ONLY for ALLOW (creation moves no money). Capture: reload the
 * stored decision by PayPal order ID and call PayPal capture ONLY for ALLOW.
 * The frontend can never force a decision — it supplies context, never
 * verdicts.
 */
@Service
public class PayPalOrderService {

  private static final Logger log = LoggerFactory.getLogger(PayPalOrderService.class);

  /**
   * Placeholder owner for order-linked rows until the authentication
   * milestone provides real users. Not a user-accounts feature.
   */
  public static final String DEMO_USER_EXTERNAL_ID = "sandbox-demo-user";

  static final String SUPPORTED_CURRENCY = "USD";

  private final PayPalClient payPalClient;
  private final DeterministicRiskEngine riskEngine;
  private final TransactionRepository transactions;
  private final PaymentContextRepository contexts;
  private final RiskAssessmentRepository assessments;
  private final UserRepository users;
  private final PaymentVerificationRepository verifications;

  public PayPalOrderService(
      PayPalClient payPalClient,
      DeterministicRiskEngine riskEngine,
      TransactionRepository transactions,
      PaymentContextRepository contexts,
      RiskAssessmentRepository assessments,
      UserRepository users,
      PaymentVerificationRepository verifications) {
    this.payPalClient = payPalClient;
    this.riskEngine = riskEngine;
    this.transactions = transactions;
    this.contexts = contexts;
    this.assessments = assessments;
    this.users = users;
    this.verifications = verifications;
  }

  @Transactional
  public CreateOrderResponseDto createOrder(CreateOrderRequestDto dto) {
    BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);

    RiskEvaluationResult result =
        riskEngine.evaluate(
            new RiskEvaluationRequest(
                amount,
                dto.getHistoricalAverage(),
                dto.isActiveCall(),
                dto.isNewRecipient(),
                dto.isRepeatedAttempts(),
                dto.isUnknownMerchant(),
                dto.isUnexpectedInvoice(),
                dto.isUrgentLanguage(),
                dto.isSuspiciousUrl(),
                dto.isSocialEngineeringDetected()));

    boolean allowed = result.recommendedAction() == RecommendedAction.ALLOW;
    Transaction transaction = storeGuardPayRecords(dto, amount, result, allowed);

    if (!allowed) {
      log.info("PayPal order blocked before creation (action={})", result.recommendedAction());
      String message =
          result.recommendedAction() == RecommendedAction.STOP
              ? "GuardPay stopped this payment before creating a PayPal order."
              : "Additional verification is required before this payment can proceed.";
      return new CreateOrderResponseDto(
          null,
          "NOT_CREATED",
          null,
          amount,
          SUPPORTED_CURRENCY,
          result.riskLevel(),
          result.recommendedAction(),
          false,
          message);
    }

    CreatedPaypalOrder created = createPaypalOrder(amount, dto.getDescription());
    transaction.setPaypalOrderId(created.orderId());
    transaction.setStatus(TransactionStatus.PENDING);
    transactions.save(transaction);
    log.info("PayPal Sandbox order created (risk={})", result.riskLevel());

    return new CreateOrderResponseDto(
        created.orderId(),
        created.status(),
        created.approvalUrl(),
        amount,
        SUPPORTED_CURRENCY,
        result.riskLevel(),
        result.recommendedAction(),
        true,
        "PayPal order created. Approve it in the Sandbox, then capture.");
  }

  /**
   * Creates a PayPal order for an already risk-cleared payment (moves no
   * money). Shared by the M8 endpoint and the guarded-payment orchestrator.
   */
  public CreatedPaypalOrder createPaypalOrder(BigDecimal amount, String description) {
    // PayPal requires at most two decimals for USD. Amounts reloaded from the
    // database (transactions.amount is NUMERIC(19,4)) would otherwise arrive as
    // e.g. "45.0000" and be rejected with DECIMAL_PRECISION, so normalize here.
    BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
    PayPalCreateOrderRequest paypalRequest =
        new PayPalCreateOrderRequest(
            "CAPTURE",
            List.of(
                new PayPalPurchaseUnit(
                    new PayPalAmount(SUPPORTED_CURRENCY, normalized.toPlainString()), description)));
    PayPalCreateOrderResponse created =
        payPalClient.post("/v2/checkout/orders", paypalRequest, PayPalCreateOrderResponse.class);
    return new CreatedPaypalOrder(created.id(), created.status(), extractApprovalUrl(created));
  }

  @Transactional
  public CaptureOrderResponseDto captureOrder(String orderId) {
    if (orderId == null || !orderId.matches("[A-Za-z0-9-]+")) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown PayPal order");
    }
    Transaction transaction =
        transactions
            .findByPaypalOrderIdForUpdate(orderId)
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
    log.info("PayPal Sandbox order captured (order linked to GuardPay transaction)");

    return new CaptureOrderResponseDto(
        orderId,
        captured.status(),
        captureId,
        transaction.getAmount(),
        transaction.getCurrency(),
        GuardedPaymentStatus.from(transaction.getStatus()));
  }

  private Transaction storeGuardPayRecords(
      CreateOrderRequestDto dto, BigDecimal amount, RiskEvaluationResult result, boolean allowed) {
    User user = demoUser();

    Transaction transaction = new Transaction();
    transaction.setUser(user);
    transaction.setAmount(amount);
    transaction.setCurrency(SUPPORTED_CURRENCY);
    transaction.setTransactionType(TransactionType.PAYMENT);
    transaction.setDescription(dto.getDescription());
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
    transaction.setStatus(
        !allowed && result.recommendedAction() == RecommendedAction.STOP
            ? TransactionStatus.CANCELLED
            : TransactionStatus.PENDING);
    transactions.save(transaction);

    PaymentContext context = new PaymentContext();
    context.setTransaction(transaction);
    context.setActiveCall(dto.isActiveCall());
    context.setNewRecipient(dto.isNewRecipient());
    context.setUnknownMerchant(dto.isUnknownMerchant());
    context.setUnexpectedInvoice(dto.isUnexpectedInvoice());
    context.setUrgentLanguage(dto.isUrgentLanguage());
    context.setSuspiciousUrl(dto.isSuspiciousUrl());
    contexts.save(context);

    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setScore(result.score());
    assessment.setRiskLevel(result.riskLevel());
    assessment.setRecommendedAction(result.recommendedAction());
    assessment.setExplanation(result.explanation());
    assessments.save(assessment);

    return transaction;
  }

  private User demoUser() {
    return users
        .findByExternalUserId(DEMO_USER_EXTERNAL_ID)
        .orElseGet(
            () -> {
              User user = new User();
              user.setExternalUserId(DEMO_USER_EXTERNAL_ID);
              user.setEmail("sandbox-demo@guardpay.local");
              user.setDisplayName("Sandbox Demo User");
              user.setCurrency(SUPPORTED_CURRENCY);
              return users.save(user);
            });
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
