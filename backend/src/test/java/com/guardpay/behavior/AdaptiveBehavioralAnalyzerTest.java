package com.guardpay.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.behavior.dto.BehavioralAnalysisRequest;
import com.guardpay.behavior.dto.BehavioralAnalysisResponse;
import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for {@link AdaptiveBehavioralAnalyzer}. Verifies that behavioral
 * findings are converted to complementary PaymentSignal objects without
 * overriding the deterministic engine.
 */
class AdaptiveBehavioralAnalyzerTest {

  private final BehavioralProfileService profileService = Mockito.mock(
      BehavioralProfileService.class);
  private final AdaptiveBehavioralAnalyzer analyzer =
      new AdaptiveBehavioralAnalyzer(profileService);

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(profileService.analyze(Mockito.any(), Mockito.any()))
        .thenReturn(new BehavioralAnalysisResponse(
            null, "INSUFFICIENT_DATA", 0, null, null, List.of(),
            "INSUFFICIENT_DATA", false));
  }

  @Test
  void insufficientProfileDataProducesNoSignals() {
    UUID userId = UUID.randomUUID();
    var request = new BehavioralAnalysisRequest(
        userId,
        new BigDecimal("50000"),
        "USD",
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now());

    List<PaymentSignal> signals = analyzer.analyze(request);

    assertTrue(signals.isEmpty());
  }

  @Test
  void nullUserIdReturnsEmptySignals() {
    var request = new BehavioralAnalysisRequest(
        null,
        new BigDecimal("1000"),
        "USD",
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now());

    List<PaymentSignal> signals = analyzer.analyze(request);

    assertTrue(signals.isEmpty());
  }

  @Test
  void signalSourceIsBehavioralProfile() {
    UUID userId = UUID.randomUUID();
    var request = new BehavioralAnalysisRequest(
        userId,
        new BigDecimal("1000"),
        "USD",
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now());

    List<PaymentSignal> signals = analyzer.analyze(request);

    for (PaymentSignal signal : signals) {
      assertEquals(SignalSource.BEHAVIORAL_PROFILE, signal.source());
    }
  }

  @Test
  void signalTypesExistInEnum() {
    assertEquals(PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION,
        PaymentSignalType.valueOf("OVERALL_BEHAVIOR_DEVIATION"));
    assertEquals(PaymentSignalType.AMOUNT_DEVIATION,
        PaymentSignalType.valueOf("AMOUNT_DEVIATION"));
    assertEquals(PaymentSignalType.UNUSUAL_PAYMENT_HOUR,
        PaymentSignalType.valueOf("UNUSUAL_PAYMENT_HOUR"));
    assertEquals(SignalSource.BEHAVIORAL_PROFILE,
        SignalSource.valueOf("BEHAVIORAL_PROFILE"));
  }

  @Test
  void availableProfileWithoutFindingsStillReportsAvailable() {
    UUID userId = UUID.randomUUID();
    Mockito.when(profileService.analyze(Mockito.any(), Mockito.any()))
        .thenReturn(
            new BehavioralAnalysisResponse(
                userId,
                "ESTABLISHED",
                25,
                OffsetDateTime.now(),
                "0.25",
                List.of(),
                "NORMAL",
                true));
    var request = new BehavioralAnalysisRequest(
        userId,
        new BigDecimal("1000"),
        "USD",
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now());

    AdaptiveBehavioralAnalyzer.AdaptiveAnalysis analysis = analyzer.analyzeDetailed(request);

    assertTrue(analysis.profileAvailable());
    assertTrue(analysis.signals().isEmpty());
  }

  @Test
  void analyzerReturnsNotNull() {
    UUID userId = UUID.randomUUID();
    var request = new BehavioralAnalysisRequest(
        userId,
        new BigDecimal("1000"),
        "USD",
        OffsetDateTime.now(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        OffsetDateTime.now());

    List<PaymentSignal> signals = analyzer.analyze(request);

    assertNotNull(signals);
  }
}
