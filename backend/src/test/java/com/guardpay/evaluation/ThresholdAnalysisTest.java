package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdAnalysisTest {

  private static final double DELTA = 0.000001;

  @Test
  void comparesActualScoresWithoutChangingOverallMetrics() {
    List<BenchmarkResult> results =
        List.of(
            result("f40", BenchmarkLabel.FRAUD, 40),
            result("f70", BenchmarkLabel.FRAUD, 70),
            result("f10", BenchmarkLabel.FRAUD, 10),
            result("l40", BenchmarkLabel.LEGITIMATE, 40),
            result("l70", BenchmarkLabel.LEGITIMATE, 70),
            result("l85", BenchmarkLabel.LEGITIMATE, 85));

    ThresholdConfiguration candidate = new ThresholdConfiguration("candidate", 50, 69, 84, false);
    ThresholdAnalysis analysis =
        ThresholdAnalysis.from(
            BenchmarkMode.DETERMINISTIC_ONLY,
            results,
            List.of(ThresholdConfiguration.production(), candidate));

    assertEquals(2, analysis.comparisons().size());

    ThresholdComparison baseline = comparison(analysis, "production-baseline");
    assertEquals(2, baseline.truePositives());
    assertEquals(1, baseline.falseNegatives());
    assertEquals(3, baseline.falsePositives());
    assertEquals(0, baseline.trueNegatives());
    assertEquals(1, baseline.fraudAllowed());
    assertEquals(2, baseline.fraudVerified());
    assertEquals(0, baseline.fraudStopped());
    assertEquals(0, baseline.legitimateAllowed());
    assertEquals(2, baseline.legitimateVerified());
    assertEquals(1, baseline.legitimateStopped());
    assertEquals(3, baseline.legitimatePaymentFriction());
    assertEquals(0.4, baseline.precision(), DELTA);
    assertEquals(2.0 / 3.0, baseline.recall(), DELTA);
    assertEquals(1.0, baseline.falsePositiveRate(), DELTA);
    assertEquals(1.0 / 3.0, baseline.falseNegativeRate(), DELTA);

    ThresholdComparison candidateComparison = comparison(analysis, "candidate");
    assertEquals(1, candidateComparison.truePositives());
    assertEquals(2, candidateComparison.falseNegatives());
    assertEquals(2, candidateComparison.falsePositives());
    assertEquals(1, candidateComparison.trueNegatives());
    assertEquals(2, candidateComparison.fraudAllowed());
    assertEquals(1, candidateComparison.fraudVerified());
    assertEquals(1, candidateComparison.legitimateAllowed());
    assertEquals(1, candidateComparison.legitimateVerified());
    assertEquals(1, candidateComparison.legitimateStopped());
    assertEquals(2, candidateComparison.legitimatePaymentFriction());
    assertEquals(1.0 / 3.0, candidateComparison.precision(), DELTA);
    assertEquals(1.0 / 3.0, candidateComparison.recall(), DELTA);
    assertEquals(2.0 / 3.0, candidateComparison.falsePositiveRate(), DELTA);
    assertEquals(2.0 / 3.0, candidateComparison.falseNegativeRate(), DELTA);

    assertEquals("production-baseline", analysis.best().get("precision"));
    assertEquals("production-baseline", analysis.best().get("recall"));
    assertEquals("production-baseline", analysis.best().get("f1"));
    assertEquals("production-baseline", analysis.best().get("accuracy"));
    assertEquals("candidate", analysis.best().get("falsePositiveRate"));
    assertEquals("production-baseline", analysis.best().get("falseNegativeRate"));
    assertEquals("production-baseline", analysis.best().get("fraudAllowed"));
    assertEquals("candidate", analysis.best().get("legitimateVerified"));
    assertEquals("production-baseline", analysis.best().get("legitimateStopped"));
    assertEquals("candidate", analysis.best().get("legitimatePaymentFriction"));
  }

  @Test
  void emptyScoresYieldNullRatesAndUnavailableBestMetrics() {
    ThresholdAnalysis analysis =
        ThresholdAnalysis.from(
            BenchmarkMode.VALIDATED_AI,
            List.of(),
            List.of(ThresholdConfiguration.production()));

    ThresholdComparison comparison = analysis.comparisons().get(0);
    assertEquals(0, comparison.evaluatedScenarios());
    assertNull(comparison.precision());
    assertNull(comparison.recall());
    assertNull(comparison.f1());
    assertNull(comparison.falsePositiveRate());
    assertNull(comparison.falseNegativeRate());
    assertEquals("n/a", analysis.best().get("precision"));
    assertEquals("n/a", analysis.best().get("recall"));
    assertEquals("n/a", analysis.best().get("f1"));
    assertEquals("production-baseline", analysis.best().get("legitimatePaymentFriction"));
  }

  private static ThresholdComparison comparison(
      ThresholdAnalysis analysis, String configuration) {
    return analysis.comparisons().stream()
        .filter(comparison -> comparison.configuration().equals(configuration))
        .findFirst()
        .orElseThrow();
  }

  private static BenchmarkResult result(String id, BenchmarkLabel label, int score) {
    return new BenchmarkResult(
        id,
        "test",
        label,
        BenchmarkMode.DETERMINISTIC_ONLY,
        score,
        RiskLevel.LOW,
        RecommendedAction.ALLOW,
        List.of(),
        score,
        RiskLevel.LOW,
        RecommendedAction.ALLOW,
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
