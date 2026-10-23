package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.merchant.Merchant;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.util.Arrays;
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
 * Collector behavior with mocked repositories — no database. Every
 * unavailable case must report a reason instead of guessing.
 */
@ExtendWith(MockitoExtension.class)
class PaymentSignalCollectorServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final UUID MERCHANT_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();

  @Mock private UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private TransactionRepository transactions;

  private PaymentSignalCollectorService collector;
  private User user;

  @BeforeEach
  void setUp() {
    collector = new PaymentSignalCollectorService(users, recipients, merchants, transactions);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    // Lenient: structural tests below never collect, behavior tests always do.
    lenient().when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    lenient()
        .when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of());
  }

  private SignalCollectionRequest request(BigDecimal amount) {
    return new SignalCollectionRequest(amount, null, USER_ID, null, null, null);
  }

  private static Transaction completed(String amount) {
    Transaction transaction = new Transaction();
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.COMPLETED);
    return transaction;
  }

  private PaymentSignal signal(AutomaticSignalCollectionResult result, PaymentSignalType type) {
    return result.signals().stream()
        .filter(s -> s.type() == type)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void unusualAmountDetectedAtFiveTimesAverage() {
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of(completed("20")));

    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("100")));

    assertTrue(result.detected(PaymentSignalType.UNUSUAL_AMOUNT));
    assertEquals(0, new BigDecimal("20").compareTo(result.historicalAverage()));
    assertTrue(signal(result, PaymentSignalType.UNUSUAL_AMOUNT).evidence().contains("100"));
  }

  @Test
  void normalAmountNotDetected() {
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of(completed("30")));

    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("100")));

    assertFalse(result.detected(PaymentSignalType.UNUSUAL_AMOUNT));
    assertNull(signal(result, PaymentSignalType.UNUSUAL_AMOUNT).reason());
  }

  @Test
  void noHistoryMeansInsufficient() {
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of());

    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("100")));

    assertFalse(result.detected(PaymentSignalType.UNUSUAL_AMOUNT));
    assertEquals("INSUFFICIENT_HISTORY", signal(result, PaymentSignalType.UNUSUAL_AMOUNT).reason());
    assertNull(result.historicalAverage());
  }

  @Test
  void excludedTransactionDoesNotCountAsHistory() {
    UUID excluded = UUID.randomUUID();
    Transaction prior = completed("10");
    Transaction current = completed("10000");
    ReflectionTestUtils.setField(current, "id", excluded);
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(List.of(prior, current));

    SignalCollectionRequest withExclusion =
        new SignalCollectionRequest(new BigDecimal("100"), null, USER_ID, null, null, excluded);
    AutomaticSignalCollectionResult result = collector.collect(withExclusion);

    assertEquals(0, new BigDecimal("10.00").compareTo(result.historicalAverage()));
    assertTrue(result.detected(PaymentSignalType.UNUSUAL_AMOUNT));
  }

  @Test
  void repeatedAttemptsDetectedAtThreshold() {
    when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of(completed("20"), completed("20")));

    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("45")));

    assertTrue(result.detected(PaymentSignalType.REPEATED_ATTEMPTS));
    assertTrue(
        signal(result, PaymentSignalType.REPEATED_ATTEMPTS).evidence().contains("3 payment attempts"));
  }

  @Test
  void repeatedAttemptsBelowThresholdNotDetected() {
    when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of(completed("20")));

    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("45")));

    assertFalse(result.detected(PaymentSignalType.REPEATED_ATTEMPTS));
    assertNull(signal(result, PaymentSignalType.REPEATED_ATTEMPTS).reason());
  }

  @Test
  void velocityUnavailableWithoutUser() {
    SignalCollectionRequest anonymous =
        new SignalCollectionRequest(new BigDecimal("45"), null, null, null, null, null);

    AutomaticSignalCollectionResult result = collector.collect(anonymous);

    assertFalse(result.detected(PaymentSignalType.REPEATED_ATTEMPTS));
    assertEquals(
        "USER_UNAVAILABLE", signal(result, PaymentSignalType.REPEATED_ATTEMPTS).reason());
  }

  private Recipient recipientOf(User owner) {
    Recipient recipient = new Recipient();
    ReflectionTestUtils.setField(recipient, "id", RECIPIENT_ID);
    recipient.setUser(owner);
    return recipient;
  }

  @Test
  void newRecipientWhenNoPriorHistory() {
    Recipient fresh = recipientOf(user);
    when(recipients.findById(RECIPIENT_ID)).thenReturn(Optional.of(fresh));
    when(transactions.findByUserAndRecipientAndStatus(user, fresh, TransactionStatus.COMPLETED))
        .thenReturn(List.of());

    SignalCollectionRequest withRecipient =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, RECIPIENT_ID, null, null);
    AutomaticSignalCollectionResult result = collector.collect(withRecipient);

    assertTrue(result.detected(PaymentSignalType.NEW_RECIPIENT));
  }

  @Test
  void knownRecipientWhenPriorHistoryExists() {
    Recipient known = recipientOf(user);
    when(recipients.findById(RECIPIENT_ID)).thenReturn(Optional.of(known));
    when(transactions.findByUserAndRecipientAndStatus(user, known, TransactionStatus.COMPLETED))
        .thenReturn(List.of(completed("45")));

    SignalCollectionRequest withRecipient =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, RECIPIENT_ID, null, null);
    AutomaticSignalCollectionResult result = collector.collect(withRecipient);

    assertFalse(result.detected(PaymentSignalType.NEW_RECIPIENT));
  }

  @Test
  void missingRecipientIdentityIsUnavailable() {
    SignalCollectionRequest anonymous =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, null, null, null);

    AutomaticSignalCollectionResult result = collector.collect(anonymous);

    assertFalse(result.detected(PaymentSignalType.NEW_RECIPIENT));
    assertEquals(
        "RECIPIENT_IDENTITY_UNAVAILABLE",
        signal(result, PaymentSignalType.NEW_RECIPIENT).reason());
  }

  @Test
  void foreignRecipientIdentityIsUnavailable() {
    User stranger = new User();
    ReflectionTestUtils.setField(stranger, "id", OTHER_USER_ID);
    when(recipients.findById(RECIPIENT_ID)).thenReturn(Optional.of(recipientOf(stranger)));

    SignalCollectionRequest withRecipient =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, RECIPIENT_ID, null, null);
    AutomaticSignalCollectionResult result = collector.collect(withRecipient);

    assertFalse(result.detected(PaymentSignalType.NEW_RECIPIENT));
    assertEquals(
        "RECIPIENT_IDENTITY_UNAVAILABLE",
        signal(result, PaymentSignalType.NEW_RECIPIENT).reason());
  }

  @Test
  void unknownMerchantWithIdentity() {
    Merchant merchant = new Merchant();
    ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
    when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
    when(transactions.findByUserAndMerchantAndStatus(
            user, merchant, TransactionStatus.COMPLETED))
        .thenReturn(List.of());

    SignalCollectionRequest withMerchant =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, null, MERCHANT_ID, null);
    AutomaticSignalCollectionResult result = collector.collect(withMerchant);

    assertTrue(result.detected(PaymentSignalType.UNKNOWN_MERCHANT));
  }

  @Test
  void knownMerchant() {
    Merchant merchant = new Merchant();
    ReflectionTestUtils.setField(merchant, "id", MERCHANT_ID);
    when(merchants.findById(MERCHANT_ID)).thenReturn(Optional.of(merchant));
    when(transactions.findByUserAndMerchantAndStatus(
            user, merchant, TransactionStatus.COMPLETED))
        .thenReturn(List.of(completed("45")));

    SignalCollectionRequest withMerchant =
        new SignalCollectionRequest(new BigDecimal("45"), null, USER_ID, null, MERCHANT_ID, null);
    AutomaticSignalCollectionResult result = collector.collect(withMerchant);

    assertFalse(result.detected(PaymentSignalType.UNKNOWN_MERCHANT));
  }

  @Test
  void missingMerchantIdentityIsUnavailable() {
    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("45")));

    assertFalse(result.detected(PaymentSignalType.UNKNOWN_MERCHANT));
    assertEquals(
        "MERCHANT_IDENTITY_UNAVAILABLE",
        signal(result, PaymentSignalType.UNKNOWN_MERCHANT).reason());
  }

  @Test
  void invoiceAndUrlReportInsufficient() {
    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("45")));

    assertFalse(result.detected(PaymentSignalType.UNEXPECTED_INVOICE));
    assertEquals(
        "INSUFFICIENT_CONTEXT", signal(result, PaymentSignalType.UNEXPECTED_INVOICE).reason());
    assertFalse(result.detected(PaymentSignalType.SUSPICIOUS_URL));
    assertEquals(
        "NO_URL_IN_CONTENT", signal(result, PaymentSignalType.SUSPICIOUS_URL).reason());
  }

  @Test
  void missingContentReportsUnavailableInsteadOfSafe() {
    AutomaticSignalCollectionResult result = collector.collect(request(new BigDecimal("45")));

    assertFalse(result.detected(PaymentSignalType.ACTIVE_CALL));
    assertEquals("NOT_AVAILABLE_YET", signal(result, PaymentSignalType.ACTIVE_CALL).reason());
    assertFalse(result.detected(PaymentSignalType.URGENT_LANGUAGE));
    assertEquals(
        "CONTENT_UNAVAILABLE", signal(result, PaymentSignalType.URGENT_LANGUAGE).reason());
    assertFalse(result.detected(PaymentSignalType.SOCIAL_ENGINEERING_DETECTED));
    assertEquals(
        "INSUFFICIENT_CONTEXT",
        signal(result, PaymentSignalType.SOCIAL_ENGINEERING_DETECTED).reason());
  }

  @Test
  void collectionRequestCarriesNoRiskBooleans() {
    // Trust boundary, enforced structurally: identities and amounts only.
    boolean anyBoolean =
        Arrays.stream(SignalCollectionRequest.class.getRecordComponents())
            .anyMatch(component -> component.getType() == boolean.class);
    assertFalse(anyBoolean);
  }
}
