package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FraudBenchmarkTest {

  private static final double DELTA = 0.000001;

  @Test
  void benchmarkRunsAllScenariosInBothModesAndWritesReports() throws IOException {
    Path dataset = Path.of("src", "test", "resources", "fraud-benchmark", "dataset", "scenarios.jsonl");
    Path output = Path.of("target", "fraud-benchmark");

    BenchmarkReport report = new FraudBenchmarkRunner().run(dataset, output);

    assertEquals(54, report.scenarioCount());
    assertTrue(report.failures().isEmpty(), String.join("\n", report.failures()));
    assertTrue(Files.exists(output.resolve("report.json")));
    assertTrue(Files.exists(output.resolve("report.md")));

    List<String> mismatches = new ArrayList<>();
    for (List<BenchmarkResult> results : report.results().values()) {
      for (BenchmarkResult result : results) {
        if (!result.scoreMatch()
            || !result.levelMatch()
            || !result.actionMatch()
            || !result.signalsMatch()) {
          mismatches.add(
              result.mode()
                  + " "
                  + result.scenarioId()
                  + " expected="
                  + result.expectedScore()
                  + "/"
                  + result.expectedLevel()
                  + "/"
                  + result.expectedAction()
                  + "/"
                  + result.expectedSignals()
                  + " actual="
                  + result.actualScore()
                  + "/"
                  + result.actualLevel()
                  + "/"
                  + result.actualAction()
                  + "/"
                  + result.actualSignals());
        }
      }
    }
    assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));

    BenchmarkMetrics deterministic =
        report.metrics().get(BenchmarkMode.DETERMINISTIC_ONLY.name());
    assertNotNull(deterministic);
    assertEquals(21, deterministic.truePositives());
    assertEquals(5, deterministic.falseNegatives());
    assertEquals(8, deterministic.falsePositives());
    assertEquals(20, deterministic.trueNegatives());
    assertEquals(21.0 / 26.0, deterministic.recall(), DELTA);
    assertEquals(21.0 / 29.0, deterministic.precision(), DELTA);
    assertEquals(42.0 / 55.0, deterministic.f1(), DELTA);
    assertEquals(41.0 / 54.0, deterministic.classificationAccuracy(), DELTA);
    assertEquals(0, deterministic.legitimateStopped());
    assertEquals(1.0, deterministic.exactActionAccuracy(), DELTA);

    BenchmarkMetrics validatedAi = report.metrics().get(BenchmarkMode.VALIDATED_AI.name());
    assertNotNull(validatedAi);
    assertEquals(23, validatedAi.truePositives());
    assertEquals(3, validatedAi.falseNegatives());
    assertEquals(9, validatedAi.falsePositives());
    assertEquals(19, validatedAi.trueNegatives());
    assertEquals(23.0 / 26.0, validatedAi.recall(), DELTA);
    assertEquals(23.0 / 32.0, validatedAi.precision(), DELTA);
    assertEquals(42.0 / 54.0, validatedAi.classificationAccuracy(), DELTA);
    assertEquals(0, validatedAi.legitimateStopped());

    assertEquals(64, report.datasetSha256().length());
    assertFalse(report.categories().get(BenchmarkMode.DETERMINISTIC_ONLY.name()).isEmpty());
    assertTrue(
        report
            .signalDimensions()
            .get(BenchmarkMode.DETERMINISTIC_ONLY.name())
            .containsKey("SOCIAL_ENGINEERING"));

    CategoryMetrics boundary =
        report.categories().get(BenchmarkMode.DETERMINISTIC_ONLY.name()).get("boundary");
    assertNotNull(boundary);
    assertEquals(1, boundary.fraudScenarios());
    assertEquals(1, boundary.falseNegatives());
    assertEquals(1.0, boundary.falseNegativeRate(), DELTA);
    assertEquals(1, boundary.legitimateScenarios());
    assertEquals(1, boundary.trueNegatives());

    ThresholdAnalysis thresholds =
        report.thresholdAnalysis().get(BenchmarkMode.DETERMINISTIC_ONLY.name());
    assertNotNull(thresholds);
    ThresholdComparison baseline = comparison(thresholds, "production-baseline");
    assertEquals(39, baseline.lowMax());
    assertEquals(69, baseline.mediumMax());
    assertEquals(84, baseline.highMax());
    assertEquals(21, baseline.truePositives());
    assertEquals(5, baseline.falseNegatives());
    assertEquals(8, baseline.falsePositives());
    assertEquals(20, baseline.trueNegatives());
    assertTrue(baseline.baseline());
    assertFalse("n/a".equals(thresholds.best().get("recall")));
    assertFalse("n/a".equals(thresholds.best().get("f1")));
    assertFalse("n/a".equals(thresholds.best().get("legitimatePaymentFriction")));

    List<String> recommendations =
        report.recommendations().get(BenchmarkMode.DETERMINISTIC_ONLY.name());
    assertNotNull(recommendations);
    assertTrue(recommendations.stream().anyMatch(item -> item.contains("Highest recall")));
    assertTrue(recommendations.stream().anyMatch(item -> item.contains("false-negative rate")));

    String json = Files.readString(output.resolve("report.json"));
    assertTrue(json.contains("\"datasetSha256\""));
    assertTrue(json.contains("\"categories\""));
    assertTrue(json.contains("\"thresholdAnalysis\""));
    assertTrue(json.contains("\"recommendations\""));
    RecipientComparison recipient =
        report.recipientComparison().get("DETERMINISTIC_ONLY_RECIPIENT");
    RecipientComparison recipientBaseline =
        report.recipientComparison().get("DETERMINISTIC_ONLY_BASELINE");
    assertNotNull(recipient);
    assertNotNull(recipientBaseline);
    assertEquals(21, recipient.metrics().truePositives());
    assertEquals(20, recipientBaseline.metrics().truePositives());
    assertEquals(1, recipient.recipientMetrics().amountAnomalyDetected());
    assertEquals(0, recipientBaseline.recipientMetrics().amountAnomalyDetected());
    assertEquals(0, recipient.recipientMetrics().knownRecipientFalseNew());
    assertEquals(4, recipient.recipientMetrics().repeatedRecipientAttemptDetected());
    assertNotNull(report.recipientComparison().get("VALIDATED_AI_RECIPIENT"));

    String markdown = Files.readString(output.resolve("report.md"));
    assertTrue(markdown.contains("## Threshold analysis"));
    assertTrue(markdown.contains("## Recipient intelligence comparison"));
    assertTrue(markdown.contains("Best by metric"));
    assertTrue(markdown.contains("Signal dimensions are cross-cutting"));

    BenchmarkResult canonicalFraud = result(report, BenchmarkMode.DETERMINISTIC_ONLY, "F25-canonical-regression");
    assertEquals(85, canonicalFraud.actualScore());
    assertEquals(RiskLevel.CRITICAL, canonicalFraud.actualLevel());
    assertEquals(RecommendedAction.STOP, canonicalFraud.actualAction());

    BenchmarkResult canonicalLegit = result(report, BenchmarkMode.DETERMINISTIC_ONLY, "L06-canonical-legit-known");
    assertEquals(20, canonicalLegit.actualScore());
    assertEquals(RiskLevel.LOW, canonicalLegit.actualLevel());
    assertEquals(RecommendedAction.ALLOW, canonicalLegit.actualAction());

    BenchmarkResult recipientAnomaly =
        result(report, BenchmarkMode.DETERMINISTIC_ONLY, "F26-known-recipient-amount-anomaly");
    assertEquals(45, recipientAnomaly.actualScore());
    assertTrue(recipientAnomaly.actualSignals().contains("RECIPIENT_AMOUNT_ANOMALY"));

    BenchmarkResult recipientInsufficientHistory =
        result(
            report,
            BenchmarkMode.DETERMINISTIC_ONLY,
            "L27-known-recipient-insufficient-history");
    assertEquals(0, recipientInsufficientHistory.actualScore());
    assertFalse(
        recipientInsufficientHistory.actualSignals().contains("RECIPIENT_AMOUNT_ANOMALY"));

    BenchmarkResult justBelowThreshold =
        result(report, BenchmarkMode.DETERMINISTIC_ONLY, "F23-just-below-missing-threshold");
    assertEquals(30, justBelowThreshold.actualScore());
    assertEquals(RiskLevel.LOW, justBelowThreshold.actualLevel());
    assertEquals(RecommendedAction.ALLOW, justBelowThreshold.actualAction());

    BenchmarkResult knownPayeeWithoutEvidence =
        result(report, BenchmarkMode.DETERMINISTIC_ONLY, "L25-known-payee-no-context");
    assertEquals(0, knownPayeeWithoutEvidence.actualScore());
    assertEquals(RiskLevel.LOW, knownPayeeWithoutEvidence.actualLevel());
    assertEquals(RecommendedAction.ALLOW, knownPayeeWithoutEvidence.actualAction());

    BenchmarkResult aiRescue = result(report, BenchmarkMode.VALIDATED_AI, "F08-paypal-lookalike-known");
    assertEquals(50, aiRescue.actualScore());
    assertEquals(RecommendedAction.VERIFY, aiRescue.actualAction());
  }

  @Test
  void filterSelectsMatchingScenarios() throws IOException {
    Path dataset = Path.of("src", "test", "resources", "fraud-benchmark", "dataset", "scenarios.jsonl");
    Path output = Path.of("target", "fraud-benchmark-filtered");

    BenchmarkReport report =
        new FraudBenchmarkRunner()
            .run(dataset, output, BenchmarkFilter.parse("F25-canonical-regression"));

    assertEquals(1, report.scenarioCount());
    assertEquals(1, report.results().get(BenchmarkMode.DETERMINISTIC_ONLY.name()).size());
    assertEquals(1, report.results().get(BenchmarkMode.VALIDATED_AI.name()).size());
  }

  @Test
  void analysisIsReproducibleAcrossRuns() throws IOException {
    Path dataset = Path.of("src", "test", "resources", "fraud-benchmark", "dataset", "scenarios.jsonl");

    BenchmarkReport first =
        new FraudBenchmarkRunner().run(dataset, Path.of("target", "fraud-benchmark-repro-a"));
    BenchmarkReport second =
        new FraudBenchmarkRunner().run(dataset, Path.of("target", "fraud-benchmark-repro-b"));

    assertEquals(first.datasetSha256(), second.datasetSha256());
    assertEquals(first.categories(), second.categories());
    assertEquals(first.signalDimensions(), second.signalDimensions());
    assertEquals(first.thresholdAnalysis(), second.thresholdAnalysis());
    assertEquals(first.recommendations(), second.recommendations());
    assertEquals(first.failures(), second.failures());

    for (BenchmarkMode mode : BenchmarkMode.values()) {
      BenchmarkMetrics firstMetrics = first.metrics().get(mode.name());
      BenchmarkMetrics secondMetrics = second.metrics().get(mode.name());
      assertEquals(firstMetrics.truePositives(), secondMetrics.truePositives());
      assertEquals(firstMetrics.falseNegatives(), secondMetrics.falseNegatives());
      assertEquals(firstMetrics.falsePositives(), secondMetrics.falsePositives());
      assertEquals(firstMetrics.trueNegatives(), secondMetrics.trueNegatives());
      assertEquals(firstMetrics.precision(), secondMetrics.precision());
      assertEquals(firstMetrics.recall(), secondMetrics.recall());
      assertEquals(firstMetrics.f1(), secondMetrics.f1());
    }
  }

  private static ThresholdComparison comparison(
      ThresholdAnalysis analysis, String configuration) {
    return analysis.comparisons().stream()
        .filter(comparison -> comparison.configuration().equals(configuration))
        .findFirst()
        .orElseThrow();
  }

  private static BenchmarkResult result(
      BenchmarkReport report, BenchmarkMode mode, String scenarioId) {
    return report.results().get(mode.name()).stream()
        .filter(result -> result.scenarioId().equals(scenarioId))
        .findFirst()
        .orElseThrow();
  }
}
