package com.guardpay.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.alert.AlertService;
import com.guardpay.merchant.Merchant;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedRiskDto;
import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.Recipient;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional tail of the guarded-payment flow: reserve the idempotency key,
 * persist the decision, and create the PayPal order only for ALLOW.
 *
 * <p>Kept separate from {@link PaymentSafetyOrchestrator} so the AI call runs
 * outside a database transaction. The key is locked before any write or
 * PayPal call, so concurrent retries cannot create duplicate orders.
 */
@Service
public class GuardedPaymentCompletionService {

  private static final Logger log =
      LoggerFactory.getLogger(GuardedPaymentCompletionService.class);

  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

  private final PayPalOrderService orderService;
  private final TransactionRepository transactions;
  private final PaymentContextRepository contexts;
  private final RiskAssessmentRepository assessments;
  private final GuardedPaymentIdempotencyRepository idempotency;
  private final GuardedPaymentLock lock;
  private final PaymentVerificationService verificationService;
  private final AlertService alertService;

  /** Legacy/test constructor: alerts are not recorded. */
  public GuardedPaymentCompletionService(
      PayPalOrderService orderService,
      TransactionRepository transactions,
      PaymentContextRepository contexts,
      RiskAssessmentRepository assessments,
      GuardedPaymentIdempotencyRepository idempotency,
      GuardedPaymentLock lock,
      PaymentVerificationService verificationService) {
    this(
        orderService,
        transactions,
        contexts,
        assessments,
        idempotency,
        lock,
        verificationService,
        null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public GuardedPaymentCompletionService(
      PayPalOrderService orderService,
      TransactionRepository transactions,
      PaymentContextRepository contexts,
      RiskAssessmentRepository assessments,
      GuardedPaymentIdempotencyRepository idempotency,
      GuardedPaymentLock lock,
      PaymentVerificationService verificationService,
      AlertService alertService) {
    this.orderService = orderService;
    this.transactions = transactions;
    this.contexts = contexts;
    this.assessments = assessments;
    this.idempotency = idempotency;
    this.lock = lock;
    this.verificationService = verificationService;
    this.alertService = alertService;
  }

  @Transactional
  public GuardedPaymentResponseDto complete(
      GuardedPaymentRequestDto dto,
      String key,
      BigDecimal amount,
      User user,
      Recipient recipient,
      Merchant merchant,
      AutomaticEvaluationResponseDto risk) {
    if (key != null) {
      lock.acquire(key);
      Optional<GuardedPaymentIdempotency> existing =
          idempotency.findByUserIdAndIdempotencyKey(user.getId(), key);
      if (existing.isPresent()) {
        return GuardedPaymentReplay.replayOrConflict(existing.get(), dto);
      }
    }

    Transaction transaction = storeRecords(dto, user, recipient, merchant, amount, risk);

    if (risk.recommendedAction() != RecommendedAction.ALLOW) {
      log.info("Guarded payment blocked before PayPal order (action={})", risk.recommendedAction());
      GuardedVerificationDto verification =
          risk.recommendedAction() == RecommendedAction.VERIFY
              ? verificationService.startChallenge(transaction)
              : null;
      return finish(
          user,
          key,
          dto,
          transaction,
          response(transaction, risk, statusFor(risk.recommendedAction()), null, verification));
    }

    CreatedPaypalOrder created =
        orderService.createPaypalOrder(
            amount,
            dto.getDescription(),
            recipient.getEmail(),
            recipient.getPaypalAccountId());
    transaction.setPaypalOrderId(created.orderId());
    transaction.setPaypalApprovalUrl(created.approvalUrl());
    transaction.setStatus(TransactionStatus.PENDING);
    transactions.save(transaction);
    log.info("Guarded payment allowed; PayPal order created (risk={})", risk.riskLevel());

    return finish(
        user,
        key,
        dto,
        transaction,
        response(
            transaction,
            risk,
            GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
            new GuardedPaypalOrderDto(created.orderId(), created.approvalUrl()),
            null));
  }

  private GuardedPaymentResponseDto finish(
      User user,
      String key,
      GuardedPaymentRequestDto dto,
      Transaction transaction,
      GuardedPaymentResponseDto response) {
    if (key == null) {
      return response;
    }
    GuardedPaymentIdempotency row = new GuardedPaymentIdempotency();
    row.setUser(user);
    row.setIdempotencyKey(key);
    row.setRequestHash(GuardedPaymentReplay.requestHash(dto));
    row.setTransaction(transaction);
    row.setResponseBody(GuardedPaymentReplay.toJson(response));
    idempotency.save(row);
    return response;
  }

  private Transaction storeRecords(
      GuardedPaymentRequestDto dto,
      User user,
      Recipient recipient,
      Merchant merchant,
      BigDecimal amount,
      AutomaticEvaluationResponseDto risk) {
    Transaction transaction = new Transaction();
    transaction.setUser(user);
    transaction.setRecipient(recipient);
    transaction.setMerchant(merchant);
    transaction.setAmount(amount);
    transaction.setCurrency(dto.getCurrency());
    transaction.setDeviceId(dto.getDeviceId());
    transaction.setDeviceFingerprint(dto.getDeviceFingerprint());
    transaction.setClientIp(dto.getClientIp());
    transaction.setGeoCountry(dto.getGeoCountry());
    transaction.setGeoCity(dto.getGeoCity());
    transaction.setAsn(dto.getAsn());
    transaction.setVpnOrProxy(dto.getVpnOrProxy());
    transaction.setTransactionType(TransactionType.PAYMENT);
    transaction.setDescription(dto.getDescription());
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
    transaction.setStatus(
        risk.recommendedAction() == RecommendedAction.STOP
            ? TransactionStatus.CANCELLED
            : TransactionStatus.PENDING);
    transactions.save(transaction);

    PaymentContext context = new PaymentContext();
    context.setTransaction(transaction);
    context.setActiveCall(flag(risk, PaymentSignalType.ACTIVE_CALL));
    context.setNewRecipient(flag(risk, PaymentSignalType.NEW_RECIPIENT));
    context.setUnknownMerchant(flag(risk, PaymentSignalType.UNKNOWN_MERCHANT));
    context.setUnexpectedInvoice(
        flag(risk, PaymentSignalType.UNEXPECTED_INVOICE)
            || flag(risk, PaymentSignalType.UNEXPECTED_INVOICE_CONTEXT));
    context.setUrgentLanguage(flag(risk, PaymentSignalType.URGENT_LANGUAGE));
    context.setSuspiciousUrl(
        flag(risk, PaymentSignalType.SUSPICIOUS_URL)
            || flag(risk, PaymentSignalType.SUSPICIOUS_URL_STRUCTURE));
    contexts.save(context);

    RiskAssessment assessment = new RiskAssessment();
    assessment.setTransaction(transaction);
    assessment.setScore(risk.score());
    assessment.setRiskLevel(risk.riskLevel());
    assessment.setRecommendedAction(risk.recommendedAction());
    assessment.setExplanation(risk.explanation());
    assessment.setFindings(serializeFindings(risk));
    assessments.save(assessment);

    if (alertService != null) {
      alertService.recordPaymentDecision(transaction, risk);
    }
    return transaction;
  }

  /** Findings are audit/display data: if they cannot be serialized, the payment still proceeds. */
  private static String serializeFindings(AutomaticEvaluationResponseDto risk) {
    if (risk.findings() == null) {
      return null;
    }
    try {
      return JSON.writeValueAsString(risk.findings());
    } catch (JsonProcessingException notSerializable) {
      log.warn("Risk findings could not be serialized; storing none");
      return null;
    }
  }

  private static boolean flag(AutomaticEvaluationResponseDto risk, PaymentSignalType type) {
    if (risk.automaticSignals() == null) {
      return false;
    }
    return risk.automaticSignals().stream()
        .anyMatch(signal -> signal.type() == type && signal.detected());
  }

  private static GuardedPaymentStatus statusFor(RecommendedAction action) {
    return switch (action) {
      case STOP -> GuardedPaymentStatus.BLOCKED;
      case VERIFY -> GuardedPaymentStatus.VERIFICATION_REQUIRED;
      case ALLOW -> GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED;
    };
  }

  private GuardedPaymentResponseDto response(
      Transaction transaction,
      AutomaticEvaluationResponseDto risk,
      GuardedPaymentStatus status,
      GuardedPaypalOrderDto paypalOrder,
      GuardedVerificationDto verification) {
    return new GuardedPaymentResponseDto(
        transaction.getId(),
        new GuardedRiskDto(
            risk.score(),
            risk.riskLevel(),
            risk.recommendedAction(),
            risk.explanation(),
            risk.automaticSignals(),
            risk.findings(),
            risk.aiStatus()),
        status,
        paypalOrder,
        verification);
  }
}
