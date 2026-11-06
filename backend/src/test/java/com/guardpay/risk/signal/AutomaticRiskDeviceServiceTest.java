package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.guardpay.device.DeviceCallState;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.transaction.TransactionRepository;
import com.guardpay.user.DemoUserService;
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
 * End-to-end deterministic device path (mocked repositories, real
 * collector + engine): trusted device state in, +15 out â€” never client data.
 */
@ExtendWith(MockitoExtension.class)
class AutomaticRiskDeviceServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock private DemoUserService demoUser;
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
            collector, new DeterministicRiskEngine(), demoUser, deviceStates, recipients, merchants,
            aiClient);
    lenient().when(demoUser.currentUser()).thenReturn(user);
  }

  private AutomaticEvaluationRequestDto request(String amount) {
    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal(amount));
    dto.setDeviceId(DEVICE_ID);
    return dto;
  }

  private void stubEmptyHistory() {
    when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    when(transactions.findByUserAndStatus(
            user, com.guardpay.transaction.TransactionStatus.COMPLETED))
        .thenReturn(List.of());
    lenient()
        .when(transactions.findByUserAndTransactionTimestampAfter(any(), any()))
        .thenReturn(List.of());
  }

  private void stubDevice(boolean active, OffsetDateTime observedAt) {
    DeviceCallState state = new DeviceCallState();
    state.setUser(user);
    state.setDeviceId(DEVICE_ID);
    state.setActiveCall(active);
    state.setObservedAt(observedAt);
    when(deviceStates.findByUserIdAndDeviceId(USER_ID, DEVICE_ID))
        .thenReturn(Optional.of(state));
  }

  @Test
  void freshActiveCallContributesExactlyFifteen() {
    stubEmptyHistory();
    stubDevice(true, OffsetDateTime.now(ZoneOffset.UTC));

    AutomaticEvaluationResponseDto response = service.evaluate(request("45"));

    assertEquals(15, response.score());
    assertEquals(RiskLevel.LOW, response.riskLevel());
    assertEquals(RecommendedAction.ALLOW, response.recommendedAction());
    assertTrue(
        response.findings().stream()
            .anyMatch(finding -> finding.code().equals("ACTIVE_CALL") && finding.triggered()));
  }

  @Test
  void staleActiveCallContributesNothing() {
    stubEmptyHistory();
    stubDevice(true, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));

    AutomaticEvaluationResponseDto response = service.evaluate(request("45"));

    assertEquals(0, response.score());
    assertTrue(
        response.automaticSignals().stream()
            .filter(signal -> signal.type() == PaymentSignalType.ACTIVE_CALL)
            .allMatch(signal -> !signal.detected() && "STALE".equals(signal.reason())));
  }

  @Test
  void unknownDeviceContributesNothing() {
    stubEmptyHistory();
    when(deviceStates.findByUserIdAndDeviceId(USER_ID, DEVICE_ID))
        .thenReturn(Optional.empty());

    AutomaticEvaluationResponseDto response = service.evaluate(request("45"));

    assertEquals(0, response.score());
  }
}
