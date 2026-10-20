package com.guardpay.payment;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.signal.AutomaticRiskEvaluationService;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single guarded-payment flow: resolve identities (learning unknown payee
 * names into the catalog so history semantics stay real) → full automatic
 * pipeline (history, device, AI, engine) → transactional completion.
 *
 * <p>Risk evaluation happens outside any database transaction so a slow AI
 * call never holds a connection or a row lock. Scoring stays in the engine,
 * PayPal protocol in {@link PayPalOrderService}, and persistence in
 * {@link GuardedPaymentCompletionService}.
 */
@Service
public class PaymentSafetyOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(PaymentSafetyOrchestrator.class);

  private final AutomaticRiskEvaluationService automaticRisk;
  private final GuardedPaymentCompletionService completion;
  private final UserRepository users;
  private final RecipientRepository recipients;
  private final MerchantRepository merchants;
  private final GuardedPaymentIdempotencyRepository idempotency;

  public PaymentSafetyOrchestrator(
      AutomaticRiskEvaluationService automaticRisk,
      GuardedPaymentCompletionService completion,
      UserRepository users,
      RecipientRepository recipients,
      MerchantRepository merchants,
      GuardedPaymentIdempotencyRepository idempotency) {
    this.automaticRisk = automaticRisk;
    this.completion = completion;
    this.users = users;
    this.recipients = recipients;
    this.merchants = merchants;
    this.idempotency = idempotency;
  }

  public GuardedPaymentResponseDto initiate(GuardedPaymentRequestDto dto) {
    return initiate(dto, null);
  }

  /**
   * Guarded initiation with optional client idempotency key. A repeated key
   * returns the stored result verbatim; a reused key with different content
   * is rejected. The completion step locks the key before side effects.
   */
  public GuardedPaymentResponseDto initiate(
      GuardedPaymentRequestDto dto, String idempotencyKey) {
    String key = normalizeKey(idempotencyKey);
    if (key != null) {
      Optional<GuardedPaymentIdempotency> existing = idempotency.findByIdempotencyKey(key);
      if (existing.isPresent()) {
        return GuardedPaymentReplay.replayOrConflict(existing.get(), dto);
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

    log.debug("Guarded payment evaluated (action={})", risk.recommendedAction());
    return completion.complete(dto, key, amount, user, recipient, merchant, risk);
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
          .orElseThrow(
              () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown recipient"));
    }
    String name = blankToNull(recipientName);
    if (name == null) {
      return null;
    }
    return recipients
        .findByUserIdAndNameIgnoreCase(user.getId(), name)
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
      return merchants
          .findById(merchantId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown merchant"));
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
}
