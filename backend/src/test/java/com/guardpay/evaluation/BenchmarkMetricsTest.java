package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkMetricsTest {

  private static final double DELTA = 0.000001;

  @Test
  void computesClassificationAndSignalMetrics() {
    List<BenchmarkResult> results =
        List.of(
            result("f1", BenchmarkLabel.FRAUD, RecommendedAction.VERIFY, List.of("NEW_RECIPIENT"), List.of("NEW_RECIPIENT")),
            result("f2", BenchmarkLabel.FRAUD, RecommendedAction.ALLOW, List.of("NEW_RECIPIENT"), List.of()),
            result("l1", BenchmarkLabel.LEGITIMATE, RecommendedAction.ALLOW, List.of(), List.of()),
            result("l2", BenchmarkLabel.LEGITIMATE, RecommendedAction.STOP, List.of(), List.of("SUSPICIOUS_URL")));

    BenchmarkMetrics metrics = BenchmarkMetrics.from(results);

    assertEquals(1, metrics.truePositives());
    assertEquals(1, metrics.falseNegatives());
    assertEquals(1, metrics.falsePositives());
    assertEquals(1, metrics.trueNegatives());
    assertEquals(1, metrics.fraudDetected());
    assertEquals(1, metrics.fraudMissed());
    assertEquals(1, metrics.legitimateAllowed());
    assertEquals(1, metrics.legitimateStopped());
    assertEquals(0.5, metrics.precision(), DELTA);
    assertEquals(0.5, metrics.recall(), DELTA);
    assertEquals(0.5, metrics.f1(), DELTA);
    assertEquals(0.5, metrics.falsePositiveRate(), DELTA);
    assertEquals(0.5, metrics.classificationAccuracy(), DELTA);
    assertEquals(1, metrics.signalTruePositives());
    assertEquals(1, metrics.signalFalsePositives());
    assertEquals(1, metrics.signalFalseNegatives());
    assertEquals(0.5, metrics.signalPrecision(), DELTA);
    assertEquals(0.5, metrics.signalRecall(), DELTA);
    assertEquals(0.5, metrics.signalF1(), DELTA);
  }

  @Test
  void undefinedRatiosAreNullAndLatencyIsZeroForEmptyInput() {
    BenchmarkMetrics metrics = BenchmarkMetrics.from(List.of());

    assertEquals(0, metrics.totalScenarios());
    assertEquals(0, metrics.evaluatedScenarios());
    assertNull(metrics.precision());
    assertNull(metrics.recall());
    assertNull(metrics.f1());
    assertNull(metrics.falsePositiveRate());
    assertNull(metrics.classificationAccuracy());
    assertNull(metrics.signalPrecision());
    assertEquals(0.0, metrics.averageLatencyMs(), DELTA);
    assertEquals(0.0, metrics.p50LatencyMs(), DELTA);
    assertEquals(0.0, metrics.p95LatencyMs(), DELTA);
  }

  private static BenchmarkResult result(
      String id,
      BenchmarkLabel label,
      RecommendedAction actualAction,
      List<String> expectedSignals,
      List<String> actualSignals) {
    return new BenchmarkResult(
        id,
        "test",
        label,
        BenchmarkMode.DETERMINISTIC_ONLY,
        0,
        RiskLevel.LOW,
        RecommendedAction.ALLOW,
        expectedSignals,
        0,
        RiskLevel.LOW,
        actualAction,
        actualSignals,
        "NOT_REQUESTED",
        1_000_000L,
        true,
        true,
        false,
        expectedSignals.equals(actualSignals),
        null);
  }
}
