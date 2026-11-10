package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.correlation.dto.CorrelationFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the correlation layer against its own scenario set. The main
 * {@code scenarios.jsonl} dataset is deliberately left untouched so the
 * exact-score assertions in {@link FraudBenchmarkTest} stay stable.
 */
class CorrelationScenarioBenchmarkTest {

  private static final Path DATASET =
      Path.of(
          "src",
          "test",
          "resources",
          "fraud-benchmark",
          "correlation",
          "correlation-scenarios.jsonl");

  private static List<BenchmarkScenario> scenarios;
  private static Map<String, Outcome> outcomes;

  @BeforeAll
  static void runScenarios() throws IOException {
    scenarios = new BenchmarkDatasetLoader().load(DATASET);
    BenchmarkHarness harness = new BenchmarkHarness();
    outcomes =
        scenarios.stream()
            .collect(
                Collectors.toMap(
                    BenchmarkScenario::id,
                    scenario ->
                        new Outcome(
                            harness
                                .evaluate(
                                    scenario,
                                    BenchmarkMode.VALIDATED_AI,
                                    true,
                                    true,
                                    Set.of(),
                                    false)
                                .response(),
                            harness
                                .evaluate(
                                    scenario,
                                    BenchmarkMode.VALIDATED_AI,
                                    true,
                                    true,
                                    Set.of(),
                                    true)
                                .response())));
  }

  @Test
  void datasetCoversEnabledCorrelationFamilies() {
    assertEquals(7, scenarios.size());
    assertEquals(7, outcomes.size());
    assertTrue(outcomes.containsKey("CF01-velocity-amount-escalation"));
    assertTrue(outcomes.containsKey("CF02-call-new-recipient-urgency"));
    assertTrue(outcomes.containsKey("CF03-phishing-url-urgency-instruction"));
    assertTrue(outcomes.containsKey("CF04-new-recipient-amount-scam"));
  }

  @Test
  void correlationNeverLowersTheDeterministicScore() {
    for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
      Outcome outcome = entry.getValue();
      assertTrue(
          outcome.with().score() >= outcome.without().score(),
          entry.getKey() + " lost points when correlation was enabled");
      assertTrue(outcome.with().score() <= 100, entry.getKey() + " exceeded the score cap");
    }
  }

  @Test
  void correlationNeverChangesADecisionDownwards() {
    for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
      Outcome outcome = entry.getValue();
      assertTrue(
          actionRank(outcome.with().recommendedAction())
              >= actionRank(outcome.without().recommendedAction()),
          entry.getKey() + " weakened its decision when correlation was enabled");
    }
  }

  @Test
  void velocityAndAmountCorrelationEscalatesAnAllowToVerify() {
    Outcome outcome = outcomes.get("CF01-velocity-amount-escalation");
    assertEquals(35, outcome.without().score());
    assertEquals(RecommendedAction.ALLOW, outcome.without().recommendedAction());
    assertTrue(outcome.with().score() > outcome.without().score());
    assertEquals(RecommendedAction.VERIFY, outcome.with().recommendedAction());
    assertEquals("AVAILABLE", outcome.with().correlationStatus());
    assertTrue(outcome.hasFinding("VELOCITY_AMOUNT_COMBO"));
  }

  @Test
  void callGuidedNewRecipientCorrelationFires() {
    Outcome outcome = outcomes.get("CF02-call-new-recipient-urgency");
    assertEquals(65, outcome.without().score());
    assertEquals(83, outcome.with().score());
    assertTrue(outcome.hasFinding("SOCIAL_ENGINEERING_CALL_COMBO"));
  }

  @Test
  void phishingContentCorrelationFires() {
    Outcome outcome = outcomes.get("CF03-phishing-url-urgency-instruction");
    assertEquals(40, outcome.without().score());
    assertEquals(53, outcome.with().score());
    assertTrue(outcome.hasFinding("PHISHING_CONTENT_COMBO"));
  }

  @Test
  void newRecipientAndAmountCorrelationFires() {
    Outcome outcome = outcomes.get("CF04-new-recipient-amount-scam");
    assertEquals(60, outcome.without().score());
    assertEquals(80, outcome.with().score());
    assertTrue(outcome.hasFinding("SOCIAL_ENGINEERING_RECIPIENT_COMBO"));
  }

  @Test
  void weakSingleSignalsDoNotTriggerCorrelation() {
    for (String id :
        List.of(
            "CF05-safe-known-recipient",
            "CF06-urgency-only-no-correlation",
            "CF07-velocity-only-no-correlation")) {
      Outcome outcome = outcomes.get(id);
      assertEquals("NO_MATCH", outcome.with().correlationStatus(), id);
      assertTrue(outcome.with().correlationFindings().isEmpty(), id);
      assertEquals(outcome.without().score(), outcome.with().score(), id);
    }
  }

  @Test
  void contributionIsBoundedAndAccountedFor() {
    for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
      Outcome outcome = entry.getValue();
      int contribution =
          outcome.with().correlationFindings().stream()
              .mapToInt(CorrelationFindingDto::contributionPoints)
              .sum();
      assertTrue(contribution >= 0, entry.getKey());
      assertTrue(contribution <= 25, entry.getKey() + " exceeded the correlation cap");
      assertEquals(
          outcome.with().score() - outcome.without().score(),
          contribution,
          entry.getKey() + " score delta does not equal the reported contribution");
    }
  }

  @Test
  void findingsCarryExplainableEvidence() {
    for (Map.Entry<String, Outcome> entry : outcomes.entrySet()) {
      for (CorrelationFindingDto finding : entry.getValue().with().correlationFindings()) {
        assertFalse(finding.title().isBlank(), entry.getKey());
        assertFalse(finding.description().isBlank(), entry.getKey());
        assertFalse(finding.explanation().isBlank(), entry.getKey());
        assertFalse(finding.participatingSignals().isEmpty(), entry.getKey());
        assertFalse(finding.evidenceFamilies().isEmpty(), entry.getKey());
        assertFalse(finding.triggeredConditions().isEmpty(), entry.getKey());
        assertEquals("1.0", finding.ruleVersion(), entry.getKey());
      }
    }
  }

  @Test
  void atLeastOneScenarioChangesAction() {
    long changed =
        outcomes.values().stream()
            .filter(
                outcome ->
                    outcome.with().recommendedAction()
                        != outcome.without().recommendedAction())
            .count();
    assertTrue(changed >= 1);
  }

  private static int actionRank(RecommendedAction action) {
    return switch (action) {
      case ALLOW -> 0;
      case VERIFY -> 1;
      case STOP -> 2;
    };
  }

  record Outcome(
      AutomaticEvaluationResponseDto without, AutomaticEvaluationResponseDto with) {

    boolean hasFinding(String code) {
      return with.correlationFindings().stream()
          .anyMatch(finding -> finding.correlationCode().equals(code));
    }
  }
}
