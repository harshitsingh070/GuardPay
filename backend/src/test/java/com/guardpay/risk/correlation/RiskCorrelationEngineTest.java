package com.guardpay.risk.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.PaymentSignalType;
import com.guardpay.risk.signal.SignalSource;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskCorrelationEngineTest {

  private final RiskCorrelationEngine engine = new RiskCorrelationEngine();

  @Test
  void deviceLocationAndRecipientCombinationFiresAccountTakeover() {
    CorrelationResult result =
        engine.evaluate(
            List.of(
                signal(PaymentSignalType.NEW_DEVICE, true),
                signal(PaymentSignalType.NEW_GEO, true),
                signal(PaymentSignalType.NEW_RECIPIENT, true)));

    assertEquals(CorrelationResult.AVAILABLE, result.status());
    assertEquals(1, result.findings().size());
    CorrelationFinding finding = result.findings().get(0);
    assertEquals("ACCOUNT_TAKEOVER_COMBO", finding.correlationCode());
    assertEquals(CorrelationCategory.ACCOUNT_TAKEOVER, finding.category());
    assertTrue(finding.contributionPoints() > 0);
    assertEquals(finding.contributionPoints(), result.contributionPoints());
  }

  @Test
  void aSingleWeakSignalDoesNotCorrelate() {
    CorrelationResult result =
        engine.evaluate(List.of(signal(PaymentSignalType.NEW_DEVICE, true)));

    assertEquals(CorrelationResult.NO_MATCH, result.status());
    assertEquals(0, result.contributionPoints());
    assertTrue(result.findings().isEmpty());
  }

  @Test
  void unavailableSignalDoesNotSatisfyARequirement() {
    CorrelationResult result =
        engine.evaluate(
            List.of(
                signal(PaymentSignalType.NEW_DEVICE, true),
                new PaymentSignal(
                    PaymentSignalType.NEW_GEO,
                    false,
                    SignalSource.NETWORK,
                    "GEO_UNAVAILABLE",
                    "No trusted country."),
                signal(PaymentSignalType.NEW_RECIPIENT, true)));

    assertEquals(CorrelationResult.NO_MATCH, result.status());
    assertEquals(0, result.contributionPoints());
  }

  @Test
  void lowConfidenceAiEvidenceCannotSatisfyARule() {
    CorrelationResult result =
        engine.evaluate(
            List.of(
                signal(PaymentSignalType.ACTIVE_CALL, true),
                signal(PaymentSignalType.NEW_RECIPIENT, true),
                ai(PaymentSignalType.URGENT_LANGUAGE, 0.3)));

    assertEquals(CorrelationResult.NO_MATCH, result.status());
  }

  @Test
  void confidentAiEvidenceParticipatesInCorrelation() {
    CorrelationResult result =
        engine.evaluate(
            List.of(
                signal(PaymentSignalType.ACTIVE_CALL, true),
                signal(PaymentSignalType.NEW_RECIPIENT, true),
                ai(PaymentSignalType.URGENT_LANGUAGE, 0.9)));

    assertEquals(CorrelationResult.AVAILABLE, result.status());
    assertEquals("SOCIAL_ENGINEERING_CALL_COMBO", result.findings().get(0).correlationCode());
    assertTrue(result.contributionPoints() > 0);
  }

  @Test
  void overlappingRulesAreDiscountedRatherThanDoubleCounted() {
    CorrelationResult result =
        engine.evaluate(
            List.of(
                signal(PaymentSignalType.NEW_RECIPIENT, true),
                signal(PaymentSignalType.UNUSUAL_AMOUNT, true),
                signal(PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY, true),
                ai(PaymentSignalType.SCAM_PATTERN, 0.9)));

    assertEquals(CorrelationResult.AVAILABLE, result.status());
    assertTrue(result.findings().size() >= 2);
    assertTrue(result.findings().stream().anyMatch(CorrelationFinding::overlapDiscounted));
    assertTrue(result.contributionPoints() <= 25);
  }

  @Test
  void contributionIsAlwaysBoundedAndAllocatedExactly() {
    List<PaymentSignal> signals = new ArrayList<>();
    signals.add(signal(PaymentSignalType.NEW_DEVICE, true));
    signals.add(signal(PaymentSignalType.NEW_GEO, true));
    signals.add(signal(PaymentSignalType.NEW_RECIPIENT, true));
    signals.add(signal(PaymentSignalType.UNUSUAL_AMOUNT, true));
    signals.add(signal(PaymentSignalType.REPEATED_ATTEMPTS, true));
    signals.add(signal(PaymentSignalType.VELOCITY_HOURLY, true));
    signals.add(signal(PaymentSignalType.VPN_PROXY, true));
    signals.add(signal(PaymentSignalType.ACTIVE_CALL, true));
    signals.add(signal(PaymentSignalType.URGENT_LANGUAGE, true));
    signals.add(signal(PaymentSignalType.SUSPICIOUS_URL, true));
    signals.add(signal(PaymentSignalType.SPLIT_PAYMENT, true));
    signals.add(signal(PaymentSignalType.UNUSUAL_PAYMENT_HOUR, true));
    signals.add(signal(PaymentSignalType.TRANSACTION_FREQUENCY_ANOMALY, true));
    signals.add(signal(PaymentSignalType.RECIPIENT_AMOUNT_ANOMALY, true));
    signals.add(ai(PaymentSignalType.SUSPICIOUS_PAYMENT_INSTRUCTION, 0.9));
    signals.add(ai(PaymentSignalType.IMPERSONATION, 0.9));
    signals.add(
        new PaymentSignal(
            PaymentSignalType.OVERALL_BEHAVIOR_DEVIATION,
            true,
            SignalSource.BEHAVIORAL_PROFILE,
            null,
            "Multiple anomalies.",
            "HIGH",
            0.75));

    CorrelationResult result = engine.evaluate(signals);

    assertEquals(CorrelationResult.AVAILABLE, result.status());
    assertTrue(result.contributionPoints() >= 0);
    assertTrue(result.contributionPoints() <= 25);
    int allocated =
        result.findings().stream().mapToInt(CorrelationFinding::contributionPoints).sum();
    assertEquals(result.contributionPoints(), allocated);
  }

  @Test
  void evaluationIsDeterministic() {
    List<PaymentSignal> signals =
        List.of(
            signal(PaymentSignalType.NEW_DEVICE, true),
            signal(PaymentSignalType.NEW_GEO, true),
            signal(PaymentSignalType.NEW_RECIPIENT, true));

    CorrelationResult first = engine.evaluate(signals);
    CorrelationResult second = engine.evaluate(signals);

    assertEquals(first.contributionPoints(), second.contributionPoints());
    assertEquals(first.aggregateStrength(), second.aggregateStrength(), 0.000001);
    assertEquals(first.findings().size(), second.findings().size());
    for (int index = 0; index < first.findings().size(); index++) {
      assertEquals(
          first.findings().get(index).correlationCode(),
          second.findings().get(index).correlationCode());
      assertEquals(
          first.findings().get(index).contributionPoints(),
          second.findings().get(index).contributionPoints());
    }
  }

  @Test
  void noSignalsIsEvidenceNeutral() {
    CorrelationResult result = engine.evaluate(List.of());

    assertEquals(CorrelationResult.NO_SIGNALS, result.status());
    assertEquals(0, result.contributionPoints());
    assertFalse(result.status().equals(CorrelationResult.AVAILABLE));
  }

  private static PaymentSignal signal(PaymentSignalType type, boolean detected) {
    return new PaymentSignal(
        type,
        detected,
        SignalSource.TRANSACTION_HISTORY,
        detected ? null : "NOT_TRIGGERED",
        "evidence");
  }

  private static PaymentSignal ai(PaymentSignalType type, double confidence) {
    return new PaymentSignal(
        type, true, SignalSource.AI, null, "evidence", "HIGH", confidence);
  }
}
