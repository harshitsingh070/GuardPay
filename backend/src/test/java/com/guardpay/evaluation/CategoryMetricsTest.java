package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryMetricsTest {

  private static final double DELTA = 0.000001;

  @Test
  void aggregatesFraudLegitimateActionsAndFriction() {
    CategoryMetrics metrics =
        CategoryMetrics.from(
            "test",
            List.of(
                result("f1", BenchmarkLabel.FRAUD, RecommendedAction.VERIFY),
                result("f2", BenchmarkLabel.FRAUD, RecommendedAction.STOP),
                result("f3", BenchmarkLabel.FRAUD, RecommendedAction.ALLOW),
                result("l1", BenchmarkLabel.LEGITIMATE, RecommendedAction.ALLOW),
                result("l2", BenchmarkLabel.LEGITIMATE, RecommendedAction.VERIFY),
                result("l3", BenchmarkLabel.LEGITIMATE, RecommendedAction.STOP)));

    assertEquals("test", metrics.category());
    assertEquals(6, metrics.totalScenarios());
    assertEquals(6, metrics.evaluatedScenarios());
    assertEquals(0, metrics.failedScenarios());
    assertEquals(3, metrics.fraudScenarios());
    assertEquals(3, metrics.legitimateScenarios());
    assertEquals(2, metrics.truePositives());
    assertEquals(1, metrics.falseNegatives());
    assertEquals(2, metrics.falsePositives());
    assertEquals(1, metrics.trueNegatives());
    assertEquals(2, metrics.allowCount());
    assertEquals(2, metrics.verifyCount());
    assertEquals(2, metrics.stopCount());
    assertEquals(1, metrics.fraudAllowed());
    assertEquals(1, metrics.fraudVerified());
    assertEquals(1, metrics.fraudStopped());
    assertEquals(1, metrics.legitimateAllowed());
    assertEquals(1, metrics.legitimateVerified());
    assertEquals(1, metrics.legitimateStopped());
    assertEquals(2, metrics.legitimatePaymentFriction());
    assertEquals(0.5, metrics.precision(), DELTA);
    assertEquals(2.0 / 3.0, metrics.recall(), DELTA);
    assertEquals(4.0 / 7.0, metrics.f1(), DELTA);
    assertEquals(0.5, metrics.accuracy(), DELTA);
    assertEquals(1.0 / 3.0, metrics.falseNegativeRate(), DELTA);
    assertEquals(2.0 / 3.0, metrics.falsePositiveRate(), DELTA);
  }

  @Test
  void keepsUndefinedRatesNullAndHandlesNoScenarios() {
    CategoryMetrics metrics = CategoryMetrics.from("empty", List.of());

    assertEquals(0, metrics.totalScenarios());
    assertEquals(0, metrics.fraudScenarios());
    assertEquals(0, metrics.legitimateScenarios());
    assertNull(metrics.precision());
    assertNull(metrics.recall());
    assertNull(metrics.f1());
    assertNull(metrics.accuracy());
    assertNull(metrics.falseNegativeRate());
    assertNull(metrics.falsePositiveRate());
    assertEquals(0, metrics.legitimatePaymentFriction());
  }

  private static BenchmarkResult result(
      String id, BenchmarkLabel label, RecommendedAction actualAction) {
    return new BenchmarkResult(
        id,
        "test",
        label,
        BenchmarkMode.DETERMINISTIC_ONLY,
        0,
        RiskLevel.LOW,
        RecommendedAction.ALLOW,
        List.of(),
        0,
        RiskLevel.LOW,
        actualAction,
        List.of(),
        "NOT_REQUESTED",
        1_000_000L,
        true,
        true,
        true,
        true,
        null);
  }
}
