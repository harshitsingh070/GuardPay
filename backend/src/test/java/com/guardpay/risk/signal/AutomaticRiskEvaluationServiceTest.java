package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Full deterministic path (real collector + real engine, mocked
 * repositories): derived evidence in, engine decision out.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticRiskEvaluationServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();

  @Mock private UserRepository users;
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
            users,
            deviceStates,
            recipients,
            merchants,
            aiClient);
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
    dto.setUserId(USER_ID);
    dto.setRecipientId(recipientId);
    return dto;
  }

  @Test
  void newRecipientWithLargeAmountYieldsVerify() {
    // History average: (50 + 87 + 120 + 80 + 98) / 5 = 87.
    when(users.existsById(USER_ID)).thenReturn(true);
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
    when(recipients.findById(RECIPIENT_ID)).thenReturn(Optional.of(fresh));
    when(transactions.findByUserAndRecipientAndStatus(fresh.getUser(), fresh, TransactionStatus.COMPLETED))
        .thenReturn(List.of());

    AutomaticEvaluationResponseDto response = service.evaluate(request("2499", RECIPIENT_ID));

    assertEquals(40, response.score());
    assertEquals(RiskLevel.MEDIUM, response.riskLevel());
    assertEquals(RecommendedAction.VERIFY, response.recommendedAction());
    assertEquals(9, response.automaticSignals().size());
    assertEquals(9, response.findings().size());
  }

  @Test
  void unknownUserIsNotFound() {
    when(users.existsById(USER_ID)).thenReturn(false);

    AutomaticEvaluationRequestDto dto = request("45", null);
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.evaluate(dto));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
  }
}
