package com.guardpay.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.transaction.TransactionType;
import com.guardpay.user.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/** Outcome-ledger learning, exclusion, poisoning, and retry behavior. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BehavioralProfileServiceLearningTest {

  private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-02-10T12:00:00Z");

  @Mock private TransactionRepository transactions;
  @Mock private UserBehaviorProfileRepository profiles;
  @Mock private UserRecipientBehaviorRepository recipientBehaviors;
  @Mock private UserHourlyBehaviorRepository hourlyBehaviors;
  @Mock private UserGeoBehaviorRepository geoBehaviors;
  @Mock private UserDeviceBehaviorRepository deviceBehaviors;
  @Mock private EntityManager entityManager;
  @Mock private BehavioralOutcomeEventRepository outcomeEvents;

  private BehavioralProfileService service;
  private UUID userId;
  private Transaction current;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    service =
        new BehavioralProfileService(
            transactions,
            profiles,
            recipientBehaviors,
            hourlyBehaviors,
            geoBehaviors,
            deviceBehaviors,
            entityManager,
            outcomeEvents,
            null);
    current = transaction("120", AS_OF.minusMinutes(1), TransactionStatus.COMPLETED);
    when(profiles.findByUserId(userId)).thenReturn(Optional.empty());
    when(profiles.saveAndFlush(any(UserBehaviorProfile.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(outcomeEvents.findByTransactionId(current.getId())).thenReturn(List.of());
    when(outcomeEvents.findNonLearnableTransactionIds(userId)).thenReturn(Set.of());
    when(outcomeEvents.findTransactionIdsByOutcomeIn(any(), any())).thenReturn(Set.of());
    when(entityManager.getReference(User.class, userId)).thenReturn(new User());
  }

  @Test
  void duplicateOutcomeIsIgnoredBeforeAnyWrite() {
    when(outcomeEvents.findByTransactionIdAndOutcome(current.getId(), BehavioralOutcome.COMPLETED))
        .thenReturn(Optional.of(new BehavioralOutcomeEvent()));

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    verify(outcomeEvents, never()).saveAndFlush(any(BehavioralOutcomeEvent.class));
    verify(profiles, never()).saveAndFlush(any(UserBehaviorProfile.class));
  }

  @Test
  void completedAllowedPaymentEntersBaseline() {
    mockHistory(history(5, "100"), current);

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    BehavioralOutcomeEvent event = capturedEvent();
    assertTrue(event.isLearnable());
    UserBehaviorProfile profile = capturedProfile();
    assertEquals(6, profile.getTransactionCount());
  }

  @Test
  void completedOutlierWithoutVerificationIsNotLearned() {
    current = transaction("5000", AS_OF.minusMinutes(1), TransactionStatus.COMPLETED);
    mockHistory(history(5, "100"), current);
    when(outcomeEvents.findNonLearnableTransactionIds(userId)).thenReturn(Set.of(current.getId()));

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    BehavioralOutcomeEvent event = capturedEvent();
    assertFalse(event.isLearnable());
    assertEquals(5, capturedProfile().getTransactionCount());
  }

  @Test
  void completedOutlierAfterExplicitVerificationIsLearned() {
    current = transaction("5000", AS_OF.minusMinutes(1), TransactionStatus.COMPLETED);
    mockHistory(history(5, "100"), current);

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.VERIFY, BehavioralOutcome.COMPLETED, true,
        "TEST");

    assertTrue(capturedEvent().isLearnable());
    assertEquals(6, capturedProfile().getTransactionCount());
  }

  @Test
  void stopDecisionIsNeverLearned() {
    mockHistory(history(5, "100"), current);
    when(outcomeEvents.findNonLearnableTransactionIds(userId)).thenReturn(Set.of(current.getId()));

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.STOP, BehavioralOutcome.COMPLETED, false,
        "TEST");

    assertFalse(capturedEvent().isLearnable());
    assertEquals(5, capturedProfile().getTransactionCount());
  }

  @Test
  void verifyWithoutExplicitVerificationIsNeverLearned() {
    mockHistory(history(5, "100"), current);
    when(outcomeEvents.findNonLearnableTransactionIds(userId)).thenReturn(Set.of(current.getId()));

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.VERIFY, BehavioralOutcome.COMPLETED, false,
        "TEST");

    assertFalse(capturedEvent().isLearnable());
  }

  @Test
  void refundExcludesPreviouslyLearnedTransaction() {
    UserBehaviorProfile existing = new UserBehaviorProfile();
    existing.setProfileVersion(1);
    existing.setTransactionCount(6);
    when(profiles.findByUserId(userId)).thenReturn(Optional.of(existing));
    when(profiles.existsByUserId(userId)).thenReturn(true);
    mockHistory(history(5, "100"), current);
    when(outcomeEvents.findNonLearnableTransactionIds(userId)).thenReturn(Set.of(current.getId()));

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.REFUNDED, false,
        "TEST");

    assertFalse(capturedEvent().isLearnable());
    assertEquals(5, existing.getTransactionCount());
  }

  @Test
  void concurrentDuplicateInsertBecomesIdempotentNoOp() {
    when(outcomeEvents.saveAndFlush(any(BehavioralOutcomeEvent.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));
    when(outcomeEvents.existsByTransactionIdAndOutcome(
            current.getId(), BehavioralOutcome.COMPLETED))
        .thenReturn(true);
    mockHistory(history(5, "100"), current);

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    verify(outcomeEvents, times(1)).saveAndFlush(any(BehavioralOutcomeEvent.class));
    verify(profiles, never()).saveAndFlush(any(UserBehaviorProfile.class));
  }

  @Test
  void optimisticLockRetriesWithoutChangingOutcome() {
    UserBehaviorProfile existing = new UserBehaviorProfile();
    existing.setProfileVersion(1);
    existing.setTransactionCount(0);
    when(profiles.findByUserId(userId)).thenReturn(Optional.of(existing));
    when(profiles.saveAndFlush(any(UserBehaviorProfile.class)))
        .thenThrow(new ObjectOptimisticLockingFailureException(UserBehaviorProfile.class, userId))
        .thenAnswer(invocation -> invocation.getArgument(0));
    mockHistory(List.of());

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    verify(profiles, times(2)).saveAndFlush(any(UserBehaviorProfile.class));
  }

  @Test
  void profileRecomputeUsesOnlyTheProfileCurrency() {
    current = transaction("120", AS_OF.minusMinutes(1), TransactionStatus.COMPLETED);
    current.setCurrency("EUR");
    List<Transaction> mixed = new ArrayList<>(history(5, "100"));
    Transaction eur1 = transaction("200", AS_OF.minusDays(2), TransactionStatus.COMPLETED);
    eur1.setCurrency("EUR");
    Transaction eur2 = transaction("300", AS_OF.minusDays(1), TransactionStatus.COMPLETED);
    eur2.setCurrency("EUR");
    mixed.add(eur1);
    mixed.add(eur2);
    mockHistory(mixed, current);

    service.updateProfileAfterOutcome(
        userId, current, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.COMPLETED, false,
        "TEST");

    UserBehaviorProfile profile = capturedProfile();
    assertEquals("EUR", profile.getCurrency());
    assertEquals(3, profile.getTransactionCount());
    assertEquals(0, new BigDecimal("200").compareTo(profile.getAmountMedian()));
  }

  @Test
  void terminalNonCompletedOutcomesAreRecordedButNeverLearnable() {
    Transaction failed = transaction("120", AS_OF.minusMinutes(1), TransactionStatus.FAILED);
    when(profiles.findByUserId(userId)).thenReturn(Optional.empty());
    mockHistory(history(5, "100"), failed);

    service.updateProfileAfterOutcome(
        userId, failed, AS_OF, RecommendedAction.ALLOW, BehavioralOutcome.FAILED, false, "TEST");

    BehavioralOutcomeEvent event = capturedEvent();
    assertFalse(event.isLearnable());
    assertEquals(BehavioralOutcome.FAILED, event.getOutcome());
  }

  private BehavioralOutcomeEvent capturedEvent() {
    ArgumentCaptor<BehavioralOutcomeEvent> captor =
        ArgumentCaptor.forClass(BehavioralOutcomeEvent.class);
    verify(outcomeEvents).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private UserBehaviorProfile capturedProfile() {
    ArgumentCaptor<UserBehaviorProfile> captor =
        ArgumentCaptor.forClass(UserBehaviorProfile.class);
    verify(profiles).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private void mockHistory(List<Transaction> history, Transaction... extra) {
    List<Transaction> all = new ArrayList<>(history);
    all.addAll(List.of(extra));
    when(transactions.findCompletedHistoryBefore(
            any(UUID.class), any(OffsetDateTime.class), any(Pageable.class)))
        .thenReturn(all);
  }

  private List<Transaction> history(int count, String amount) {
    List<Transaction> history = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      history.add(
          transaction(amount, AS_OF.minusDays(10 - i).minusHours(1), TransactionStatus.COMPLETED));
    }
    return history;
  }

  private static Transaction transaction(
      String amount, OffsetDateTime timestamp, TransactionStatus status) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(status);
    transaction.setTransactionTimestamp(timestamp);
    transaction.setTransactionType(TransactionType.PAYMENT);
    return transaction;
  }
}
