package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The behavioral outcome ledger must remove refunded, reversed, and disputed
 * payments from the learned baseline. Without that exclusion a refunded
 * outlier would keep inflating the baseline and hide later anomalies.
 */
class BehavioralLedgerBenchmarkTest {

  private static final Path DATASET =
      Path.of("src", "test", "resources", "fraud-benchmark", "dataset", "scenarios.jsonl");

  @Test
  void refundedHistoryIsExcludedFromTheBehavioralBaseline() throws IOException {
    BenchmarkScenario scenario = scenario("B06-zero-mad-distribution");
    BenchmarkHarness harness = new BenchmarkHarness();

    List<String> withBaseline =
        triggeredSignals(
            harness.evaluate(scenario, BenchmarkMode.DETERMINISTIC_ONLY, true, true).response());
    assertTrue(
        withBaseline.contains("FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE"),
        () -> withBaseline.toString());

    List<String> refundedBaseline =
        triggeredSignals(
            harness
                .evaluate(
                    scenario,
                    BenchmarkMode.DETERMINISTIC_ONLY,
                    true,
                    true,
                    BenchmarkHarness.historyIds(scenario.input()))
                .response());
    assertFalse(refundedBaseline.contains("AMOUNT_DEVIATION"), () -> refundedBaseline.toString());
    assertFalse(
        refundedBaseline.contains("FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE"),
        () -> refundedBaseline.toString());
    assertTrue(refundedBaseline.contains("NEW_RECIPIENT"), () -> refundedBaseline.toString());
    assertTrue(refundedBaseline.contains("UNKNOWN_MERCHANT"), () -> refundedBaseline.toString());
  }

  @Test
  void ledgerExclusionOnlyRemovesBehavioralEvidence() throws IOException {
    BenchmarkScenario scenario = scenario("B02-first-time-high-value");
    BenchmarkHarness harness = new BenchmarkHarness();

    AutomaticEvaluationResponseDto withBaseline =
        harness.evaluate(scenario, BenchmarkMode.DETERMINISTIC_ONLY, true, true).response();
    AutomaticEvaluationResponseDto refundedBaseline =
        harness
            .evaluate(
                scenario,
                BenchmarkMode.DETERMINISTIC_ONLY,
                true,
                true,
                BenchmarkHarness.historyIds(scenario.input()))
            .response();

    assertTrue(triggeredSignals(withBaseline).contains("FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE"));
    assertFalse(triggeredSignals(refundedBaseline).contains("AMOUNT_DEVIATION"));
    assertFalse(
        triggeredSignals(refundedBaseline).contains("FIRST_PAYMENT_TO_RECIPIENT_HIGH_VALUE"));
    assertTrue(triggeredSignals(refundedBaseline).contains("LARGE_AMOUNT"));
    assertTrue(triggeredSignals(refundedBaseline).contains("NEW_RECIPIENT"));
    assertTrue(
        refundedBaseline.score() < withBaseline.score(),
        () ->
            "behavioral points should drop when the baseline is gone: "
                + refundedBaseline.score()
                + " >= "
                + withBaseline.score());
  }

  private static BenchmarkScenario scenario(String id) throws IOException {
    return new BenchmarkDatasetLoader().load(DATASET).stream()
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("benchmark scenario not found: " + id));
  }

  private static List<String> triggeredSignals(AutomaticEvaluationResponseDto response) {
    return response.findings().stream()
        .filter(RiskFindingDto::triggered)
        .map(RiskFindingDto::code)
        .toList();
  }
}
