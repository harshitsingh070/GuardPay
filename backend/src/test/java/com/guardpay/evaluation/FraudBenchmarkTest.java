package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    assertEquals(49, report.scenarioCount());
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
    assertEquals(19, deterministic.truePositives());
    assertEquals(6, deterministic.falseNegatives());
    assertEquals(8, deterministic.falsePositives());
    assertEquals(16, deterministic.trueNegatives());
    assertEquals(0.76, deterministic.recall(), DELTA);
    assertEquals(19.0 / 27.0, deterministic.precision(), DELTA);
    assertEquals(35.0 / 49.0, deterministic.classificationAccuracy(), DELTA);
    assertEquals(0, deterministic.legitimateStopped());
    assertEquals(1.0, deterministic.exactActionAccuracy(), DELTA);

    BenchmarkMetrics validatedAi = report.metrics().get(BenchmarkMode.VALIDATED_AI.name());
    assertNotNull(validatedAi);
    assertEquals(21, validatedAi.truePositives());
    assertEquals(4, validatedAi.falseNegatives());
    assertEquals(9, validatedAi.falsePositives());
    assertEquals(15, validatedAi.trueNegatives());
    assertEquals(0.84, validatedAi.recall(), DELTA);
    assertEquals(0.70, validatedAi.precision(), DELTA);
    assertEquals(36.0 / 49.0, validatedAi.classificationAccuracy(), DELTA);
    assertEquals(0, validatedAi.legitimateStopped());

    BenchmarkResult canonicalFraud = result(report, BenchmarkMode.DETERMINISTIC_ONLY, "F25-canonical-regression");
    assertEquals(85, canonicalFraud.actualScore());
    assertEquals(RiskLevel.CRITICAL, canonicalFraud.actualLevel());
    assertEquals(RecommendedAction.STOP, canonicalFraud.actualAction());

    BenchmarkResult canonicalLegit = result(report, BenchmarkMode.DETERMINISTIC_ONLY, "L06-canonical-legit-known");
    assertEquals(20, canonicalLegit.actualScore());
    assertEquals(RiskLevel.LOW, canonicalLegit.actualLevel());
    assertEquals(RecommendedAction.ALLOW, canonicalLegit.actualAction());

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

  private static BenchmarkResult result(
      BenchmarkReport report, BenchmarkMode mode, String scenarioId) {
    return report.results().get(mode.name()).stream()
        .filter(result -> result.scenarioId().equals(scenarioId))
        .findFirst()
        .orElseThrow();
  }
}
