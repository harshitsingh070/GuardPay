package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.Recipient;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
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
 * Full deterministic path (real collector + real engine, mocked
 * repositories): derived evidence in, engine decision out.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticRiskEvaluationServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();

  @Mock private DemoUserService demoUser;
  @Mock private com.guardpay.user.UserRepository users;
  @Mock private RecipientRepository recipients;
  @Mock private MerchantRepository merchants;
  @Mock private TransactionRepository transactions;

  @Mock private DeviceCallStateRepository deviceStates;

  @Mock private AiAnalysisClient aiClient;

  private AutomaticRiskEvaluationService service;
  private User user;

  @BeforeEach
  void setUp() {
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    PaymentSignalCollectorService collector =
        new PaymentSignalCollectorService(users, recipients, merchants, transactions);
    service =
        new AutomaticRiskEvaluationService(
            collector,
            new DeterministicRiskEngine(),
            demoUser,
            deviceStates,
            recipients,
            merchants,
            aiClient);
    lenient().when(demoUser.currentUser()).thenReturn(user);
    lenient()
        .when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of());
  }

  private static Transaction completed(String amount) {
    Transaction transaction = new Transaction();
    transaction.setAmount(new BigDecimal(amount));
    transaction.setCurrency("USD");
    transaction.setStatus(TransactionStatus.COMPLETED);
    return transaction;
  }

  private AutomaticEvaluationRequestDto request(String amount, UUID recipientId) {
    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal(amount));
    dto.setRecipientId(recipientId);
    return dto;
  }

  @Test
  void newRecipientWithLargeAmountYieldsVerify() {
    // History average: (50 + 87 + 120 + 80 + 98) / 5 = 87.
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(user, TransactionStatus.COMPLETED))
        .thenReturn(
            List.of(
                completed("50"),
                completed("87"),
                completed("120"),
                completed("80"),
                completed("98")));
    Recipient fresh = new Recipient();
    ReflectionTestUtils.setField(fresh, "id", RECIPIENT_ID);
    fresh.setUser(user);
    when(recipients.findByIdAndUserId(RECIPIENT_ID, USER_ID)).thenReturn(Optional.of(fresh));

    AutomaticEvaluationResponseDto response = service.evaluate(request("2499", RECIPIENT_ID));

    // Behavioral profiling may add AMOUNT_DEVIATION for personalized deviation.
    assertEquals(RiskLevel.MEDIUM, response.riskLevel());
    assertEquals(RecommendedAction.VERIFY, response.recommendedAction());
    // Signal count includes the 7 new behavioral signal types.
    assertTrue(response.automaticSignals().size() >= 20);
    assertTrue(response.findings().size() >= 20);
    // Verify key signals are present.
    assertTrue(response.automaticSignals().stream()
        .anyMatch(s -> s.type() == PaymentSignalType.NEW_RECIPIENT && s.detected()));
    assertTrue(response.automaticSignals().stream()
        .anyMatch(s -> s.type() == PaymentSignalType.UNUSUAL_AMOUNT && s.detected()));
  }

}


