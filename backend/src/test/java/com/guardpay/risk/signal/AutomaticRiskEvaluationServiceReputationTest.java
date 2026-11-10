package com.guardpay.risk.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.guardpay.ai.AiAnalysisClient;
import com.guardpay.device.DeviceCallStateRepository;
import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.MatchStatus;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;
import com.guardpay.intelligence.service.RecipientReputationEvidence;
import com.guardpay.intelligence.service.RecipientReputationLookup;
import com.guardpay.merchant.MerchantRepository;
import com.guardpay.recipient.RecipientRepository;
import com.guardpay.risk.DeterministicRiskEngine;
import com.guardpay.risk.signal.dto.AutomaticEvaluationRequestDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AutomaticRiskEvaluationServiceReputationTest {

  @Test
  void reputationIsReturnedAndIncludedInTheScore() {
    UUID userId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    RecipientReputationLookup lookup = mock(RecipientReputationLookup.class);
    when(lookup.lookup(userId, recipientId))
        .thenReturn(
            new RecipientReputationEvidence(
                MatchStatus.MATCH_FOUND,
                ReputationStatus.REPORTED,
                ReputationCategory.ELEVATED_RISK,
                40,
                10,
                IdentifierMatchLevel.EXACT_VERIFIED_MATCH,
                IdentityConfidence.VERY_HIGH,
                2,
                2,
                "GLOBAL_RECIPIENT_REPORTED_EXACT_VERIFIED_MATCH"));

    AutomaticEvaluationResponseDto response = service(userId, lookup).evaluate(dto(recipientId));

    assertNotNull(response.recipientReputation());
    assertEquals("REPORTED", response.recipientReputation().reputationStatus());
    assertEquals(10, response.recipientReputation().points());
    assertEquals(30, response.score());
    assertTrue(
        response.automaticSignals().stream()
            .anyMatch(
                signal ->
                    signal.type() == PaymentSignalType.GLOBAL_RECIPIENT_REPORTED
                        && signal.detected()
                        && signal.source() == SignalSource.GLOBAL_INTELLIGENCE));
  }

  @Test
  void lookupFailureIsUnavailableAndDoesNotChangeTheDecision() {
    UUID userId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    RecipientReputationLookup lookup = mock(RecipientReputationLookup.class);
    when(lookup.lookup(userId, recipientId)).thenThrow(new IllegalStateException("boom"));

    AutomaticEvaluationResponseDto response = service(userId, lookup).evaluate(dto(recipientId));

    assertNotNull(response.recipientReputation());
    assertEquals("UNAVAILABLE", response.recipientReputation().status());
    assertEquals(0, response.recipientReputation().points());
    assertEquals(20, response.score());
    assertTrue(
        response.automaticSignals().stream()
            .noneMatch(signal -> signal.source() == SignalSource.GLOBAL_INTELLIGENCE));
  }

  private static AutomaticRiskEvaluationService service(
      UUID userId, RecipientReputationLookup lookup) {
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
    ReflectionTestUtils.setField(user, "id", userId);
    when(demoUser.currentUser()).thenReturn(user);

    return new AutomaticRiskEvaluationService(
        collector,
        new DeterministicRiskEngine(),
        demoUser,
        mock(DeviceCallStateRepository.class),
        mock(RecipientRepository.class),
        mock(MerchantRepository.class),
        mock(AiAnalysisClient.class),
        null,
        null,
        null,
        lookup);
  }

  private static AutomaticEvaluationRequestDto dto(UUID recipientId) {
    AutomaticEvaluationRequestDto dto = new AutomaticEvaluationRequestDto();
    dto.setAmount(new BigDecimal("45"));
    dto.setCurrency("USD");
    dto.setRecipientId(recipientId);
    return dto;
  }
}
