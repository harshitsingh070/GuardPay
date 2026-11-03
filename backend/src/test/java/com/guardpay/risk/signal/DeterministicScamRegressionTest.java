package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskEvaluationResult;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * End-to-end deterministic detection regression: a canonical scam must reach
 * STOP with no AI service and no device observation, while a normal invoice to
 * a known counterparty must not.
 */
@ExtendWith(MockitoExtension.class)
class DeterministicScamRegressionTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final UUID MERCHANT_ID = UUID.randomUUID();

  @Mock private UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private TransactionRepository transactions;

  private PaymentSignalCollectorService collector;
  private DeterministicRiskEngine engine;
  private User user;
  private Recipient recipient;
  private Merchant merchant;

  @BeforeEach
  void setUp() {
    collector =
        new PaymentSignalCollectorService(
            users, recipients, merchants, transactions, new DeterministicContentAnalyzer());
    engine = new DeterministicRiskEngine();

    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    recipient = new Recipient();
    ReflectionTestUtils.setField(recipient, "id", RECIPIENT_ID);
    recipient.setUser(user);
    recipient.setName("Recipient");
    merchant = new Merchant();
    ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
    merchant.setName("Merchant");

    lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    lenient().when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.of(recipient));
    lenient().when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
    lenient()
        .when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of());
    lenient()
        .when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());
  }

  private RiskEvaluationResult evaluate(
      BigDecimal amount, String text, String url) {
    SignalCollectionRequest request =
        new SignalCollectionRequest(
            amount, "USD", USER_ID, RECIPIENT_ID, MERCHANT_ID, null, text, url);
    AutomaticSignalCollectionResult collection =
        collector.collect(request, new DeviceCallSignal(null, "NOT_AVAILABLE"));
    return engine.evaluate(
        AutomaticSignalAdapter.toRiskEvaluationRequest(amount, collection));
  }

  @Test
  void canonicalScamStopsWithoutAiOrDevice() {
    RiskEvaluationResult result =
        evaluate(
            new BigDecimal("2499"),
            "You did not request this invoice. URGENT: your account is locked. "
                + "Pay immediately with gift cards and send the codes.",
            "http://paypal.com.secure-login.example/pay");

    assertEquals(85, result.score());
    assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    assertEquals(RecommendedAction.STOP, result.recommendedAction());
    assertTrue(
        result.findings().stream()
            .anyMatch(f -> f.code().equals("SOCIAL_ENGINEERING") && f.triggered()));
  }

  @Test
  void normalInvoiceToKnownCounterpartyDoesNotStop() {
    List<Transaction> priorPayments =
        List.of(completed("45"), completed("45"), completed("45"));
    when(transactions.findRecipientCompletedHistory(
            any(), any(), any(), any(), any(), any()))
        .thenReturn(priorPayments);
    when(transactions.findByUserAndMerchantAndStatus(
            user, merchant, TransactionStatus.COMPLETED))
        .thenReturn(List.of(priorPayments.get(0)));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(priorPayments);

    RiskEvaluationResult result =
        evaluate(
            new BigDecimal("2499"),
            "Thanks for your business. Invoice #123 for $45 is attached.",
            "https://shop.example.com/invoice/123");

    assertNotEquals(RecommendedAction.STOP, result.recommendedAction());
    assertEquals(RiskLevel.LOW, result.riskLevel());
    assertEquals(RecommendedAction.ALLOW, result.recommendedAction());
    // The account-wide LARGE_AMOUNT signal already covers this amount, so the
    // recipient-specific signal is deliberately suppressed (no double count).
    assertFalse(
        result.findings().stream()
            .anyMatch(
                f -> f.code().equals("RECIPIENT_AMOUNT_ANOMALY") && f.triggered()));
  }

  private static Transaction completed(String amount) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setTransactionTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(2));
    return transaction;
  }
}

