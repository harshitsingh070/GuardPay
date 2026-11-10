package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.correlation.RiskCorrelationEngine;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AutomaticRiskEvaluationServiceCorrelationTest {

  @Test
  void failingCorrelationFallsBackToDeterministicResult() {
    PaymentSignalCollectorService collector = mock(PaymentSignalCollectorService.class);
    when(collector.collect(any(), any()))
        .thenReturn(
            new AutomaticSignalCollectionResult(
                List.of(
                    new PaymentSignal(
                        PaymentSignalType.NEW_RECIPIENT,
                        true,
                        SignalSource.TRANSACTION_HISTORY,
                        null,
                        "New recipient.")),
                null));

    DemoUserService demoUser = mock(DemoUserService.class);
    User user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    when(demoUser.currentUser()).thenReturn(user);

    RiskCorrelationEngine correlation = mock(RiskCorrelationEngine.class);
    when(correlation.evaluate(any())).thenThrow(new IllegalStateException("boom"));

    AutomaticRiskEvaluationService service =
        new AutomaticRiskEvaluationService(
            collector,
            new DeterministicRiskEngine(),
            demoUser,
            mock(DeviceCallStateRepository.class),
            mock(RecipientRepository.class),
            mock(MerchantRepository.class),
            mock(AiAnalysisClient.class),
            null,
            null,
            correlation);

    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal("45"));
    dto.setCurrency("USD");

    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    assertEquals(20, response.score());
    assertEquals("UNAVAILABLE", response.correlationStatus());
    assertTrue(response.correlationFindings().isEmpty());
  }

  @Test
  void correlationFindingsAreReturnedWhenTheEngineSucceeds() {
    PaymentSignalCollectorService collector = mock(PaymentSignalCollectorService.class);
    when(collector.collect(any(), any()))
        .thenReturn(
            new AutomaticSignalCollectionResult(
                List.of(
                    new PaymentSignal(
                        PaymentSignalType.NEW_DEVICE,
                        true,
                        SignalSource.TRANSACTION_HISTORY,
                        null,
                        "New device."),
                    new PaymentSignal(
                        PaymentSignalType.NEW_GEO,
                        true,
                        SignalSource.TRANSACTION_HISTORY,
                        null,
                        "New country."),
                    new PaymentSignal(
                        PaymentSignalType.NEW_RECIPIENT,
                        true,
                        SignalSource.TRANSACTION_HISTORY,
                        null,
                        "New recipient.")),
                null));

    DemoUserService demoUser = mock(DemoUserService.class);
    User user = new User();
    ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
    when(demoUser.currentUser()).thenReturn(user);

    AutomaticRiskEvaluationService service =
        new AutomaticRiskEvaluationService(
            collector,
            new DeterministicRiskEngine(),
            demoUser,
            mock(DeviceCallStateRepository.class),
            mock(RecipientRepository.class),
            mock(MerchantRepository.class),
            mock(AiAnalysisClient.class),
            null,
            null,
            new RiskCorrelationEngine());

    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal("45"));
    dto.setCurrency("USD");

    AutomaticEvaluationResponseDto response = service.evaluate(dto);

    assertEquals("AVAILABLE", response.correlationStatus());
    assertEquals(1, response.correlationFindings().size());
    assertEquals(
        "ACCOUNT_TAKEOVER_COMBO", response.correlationFindings().get(0).correlationCode());
    assertEquals(75, response.score());
  }
}
