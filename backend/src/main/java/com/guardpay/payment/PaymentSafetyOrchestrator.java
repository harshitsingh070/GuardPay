package com.guardpay.payment;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedRiskDto;
import com.guardpay.paypal.dto.CreatedPaypalOrder;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskAssessment;
import com.guardpay.risk.RiskAssessmentRepository;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.risk.signal.dto.PaymentSignalDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single guarded-payment flow: resolve identities (learning unknown payee
 * names into the catalog so history semantics stay real) → full automatic
 * pipeline (history, device, AI, engine) → persist once → create the PayPal
 * order only for ALLOW.
 *
 * <p>Delegates scoring to the engine and PayPal creation to
 * {@link PayPalOrderService}; it implements no risk rules and no PayPal
 * protocol itself.
 */
@Service
public class PaymentSafetyOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(PaymentSafetyOrchestrator.class);

  private static final ObjectMapper JSON = new ObjectMapper();

  private final AutomaticRiskEvaluationService automaticRisk;
  private final PayPalOrderService orderService;
  private final TransactionRepository transactions;
  private final PaymentContextRepository contexts;
  private final RiskAssessmentRepository assessments;
  private final UserRepository users;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final GuardedPaymentIdempotencyRepository idempotency;

  public PaymentSafetyOrchestrator(
      AutomaticRiskEvaluationService automaticRisk,
      PayPalOrderService orderService,
      TransactionRepository transactions,
      PaymentContextRepository contexts,
      RiskAssessmentRepository assessments,
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      GuardedPaymentIdempotencyRepository idempotency) {
    this.automaticRisk = automaticRisk;
    this.orderService = orderService;
    this.transactions = transactions;
    this.contexts = contexts;
    this.assessments = assessments;
    this.users = users;
    this.recipients = recipients;
    this.merchants = merchants;
    this.idempotency = idempotency;
  }

  @Transactional
  public GuardedPaymentResponseDto initiate(GuardedPaymentRequestDto dto) {
    return initiate(dto, null);
  }

  /**
   * Guarded initiation with optional client idempotency key. A repeated key
   * returns the stored result verbatim; a reused key with different content
   * is rejected. The UNIQUE constraint arbitrates delivery races.
   */
  @Transactional
  public GuardedPaymentResponseDto initiate(GuardedPaymentRequestDto dto, String idempotencyKey) {
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      Optional<GuardedPaymentIdempotency> existing = idempotency.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        return replayOrConflict(existing.get(), dto);
      }
    }

    BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
    User user = resolveUser(dto.getUserId());
    Recipient recipient = resolveRecipient(user, dto.getRecipientId(), dto.getRecipientName());
    Merchant merchant = resolveMerchant(dto.getMerchantId(), dto.getMerchantName());

    AutomaticEvaluationRequestDto auto = new AutomaticEvaluationRequestDto();
    auto.setAmount(amount);
    auto.setCurrency(dto.getCurrency());
    auto.setUserId(user.getId());
    auto.setRecipientId(recipient != null ? recipient.getId() : null);
    auto.setMerchantId(merchant != null ? merchant.getId() : null);
    auto.setDeviceId(dto.getDeviceId());
    auto.setContentText(dto.getContentText());
    auto.setContentUrl(dto.getContentUrl());
    AutomaticEvaluationResponseDto risk = automaticRisk.evaluate(auto);

    Transaction transaction = storeRecords(dto, user, recipient, merchant, amount, risk);

    if (risk.recommendedAction() != RecommendedAction.ALLOW) {
      log.info("Guarded payment blocked before PayPal order (action={})", risk.recommendedAction());
      return finish(key, dto, transaction,
          response(transaction, risk, statusFor(risk.recommendedAction()), null));
    }

    CreatedPaypalOrder created =
        orderService.createPaypalOrder(amount, dto.getDescription());
    transaction.setPaypalOrderId(created.orderId());
    transaction.setStatus(TransactionStatus.PENDING);
    transactions.save(transaction);
    log.info("Guarded payment allowed; PayPal order created (risk={})", risk.riskLevel());

    return finish(
        key,
        dto,
        transaction,
        response(
            transaction,
            risk,
            GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
            new GuardedPaypalOrderDto(created.orderId(), created.approvalUrl())));
  }

  private GuardedPaymentResponseDto finish(
      String key,
      GuardedPaymentRequestDto dto,
      Transaction transaction,
      GuardedPaymentResponseDto response) {
    if (key == null) {
      return response;
    }
    GuardedPaymentIdempotency row = new GuardedPaymentIdempotency();
    row.setIdempotencyKey(key);
    row.setRequestHash(requestHash(dto));
    row.setTransaction(transaction);
    try {
      row.setResponseBody(JSON.writeValueAsString(response));
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("Guarded payment response is not serializable");
    }
    try {
      idempotency.save(row);
    } catch (DataIntegrityViolationException race) {
      // Lost a delivery race: the winner's stored result is authoritative.
      GuardedPaymentIdempotency winner =
          idempotency
              .findByIdempotencyKey(key)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.CONFLICT, "Payment is already being processed"));
      return replayOrConflict(winner, dto);
    }
    return response;
  }

  private GuardedPaymentResponseDto replayOrConflict(
      GuardedPaymentIdempotency row, GuardedPaymentRequestDto dto) {
    if (!row.getRequestHash().equals(requestHash(dto))) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Idempotency key was already used with a different payment");
    }
    try {
      return JSON.readValue(row.getResponseBody(), GuardedPaymentResponseDto.class);
    } catch (JsonProcessingException unreadable) {
      throw new IllegalStateException("Stored guarded payment response is not readable");
    }
  }

  private static String normalizeKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    String key = idempotencyKey.trim();
    if (key.length() > 128) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Idempotency key must be 128 characters or fewer");
    }
    return key;
  }

  static String requestHash(GuardedPaymentRequestDto dto) {
    String canonical =
        String.join(
            "|",
            dto.getAmount().stripTrailingZeros().toPlainString(),
            nullToEmpty(dto.getCurrency()),
            nullToEmpty(dto.getUserId()),
            nullToEmpty(dto.getRecipientId()),
            lowerOrEmpty(dto.getRecipientName()),
            nullToEmpty(dto.getMerchantId()),
            lowerOrEmpty(dto.getMerchantName()),
            nullToEmpty(dto.getDeviceId()),
            nullToEmpty(dto.getContentText()),
            nullToEmpty(dto.getContentUrl()),
            nullToEmpty(dto.getDescription()));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is not available");
    }
  }

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String lowerOrEmpty(String value) {
    return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private User resolveUser(UUID userId) {
    if (userId != null) {
      return users
          .findById(userId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user"));
    }
    // No authentication yet: shared demo owner (same documented placeholder as orders).
    return users
        .findByExternalUserId(PayPalOrderService.DEMO_USER_EXTERNAL_ID)
        .orElseGet(
            () -> {
              User user = new User();
              user.setExternalUserId(PayPalOrderService.DEMO_USER_EXTERNAL_ID);
              user.setEmail("sandbox-demo@guardpay.local");
              user.setDisplayName("Sandbox Demo User");
              user.setCurrency("USD");
              return users.save(user);
            });
  }

  private Recipient resolveRecipient(User user, UUID recipientId, String recipientName) {
    if (recipientId != null) {
      return recipients
          .findById(recipientId)
          .filter(candidate -> isOwnedBy(candidate.getUser(), user))
          .orElse(null);
    }
    String name = blankToNull(recipientName);
    if (name == null) {
      return null;
    }
    return user.getRecipients().stream()
        .filter(candidate -> name.equalsIgnoreCase(candidate.getName()))
        .findFirst()
        .orElseGet(
            () -> {
              Recipient fresh = new Recipient();
              fresh.setUser(user);
              fresh.setName(name);
              return recipients.save(fresh);
            });
  }

  private Merchant resolveMerchant(UUID merchantId, String merchantName) {
    if (merchantId != null) {
      return merchants.findById(merchantId).orElse(null);
    }
    String name = blankToNull(merchantName);
    if (name == null) {
      return null;
    }
    return merchants
        .findByNameIgnoreCase(name)
        .orElseGet(
            () -> {
              Merchant fresh = new Merchant();
              fresh.setName(name);
              return merchants.save(fresh);
            });
  }

  private static boolean isOwnedBy(User candidate, User owner) {
    return candidate != null
        && owner != null
        && owner.getId() != null
        && owner.getId().equals(candidate.getId());
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
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
    assessments.save(assessment);

    return transaction;
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
      GuardedPaypalOrderDto paypalOrder) {
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
        paypalOrder);
  }
}
