package com.guardpay.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HeldOutBenchmarkTest {

  private static final double DELTA = 0.000001;
  private static final Path DEV_DATASET =
      Path.of("src", "test", "resources", "fraud-benchmark", "dataset", "scenarios.jsonl");
  private static final Path HELD_OUT_DATASET =
      Path.of("src", "test", "resources", "fraud-benchmark", "held-out", "scenarios.jsonl");
  private static final Path OUTPUT = Path.of("target", "fraud-benchmark");

  @Test
  void heldOutRunsSeparatelyWithFixedPolicyAndProducesComparison() throws IOException {
    BenchmarkReport dev =
        new FraudBenchmarkRunner().run(DEV_DATASET, OUTPUT, BenchmarkDatasetRole.DEV);
    BenchmarkReport heldOut =
        new FraudBenchmarkRunner()
            .run(
                HELD_OUT_DATASET,
                OUTPUT.resolve("held-out"),
                BenchmarkDatasetRole.HELD_OUT);

    assertEquals(BenchmarkDatasetRole.DEV, dev.datasetRole());
    assertEquals(BenchmarkDatasetRole.HELD_OUT, heldOut.datasetRole());
    assertEquals(54, dev.scenarioCount());
    assertEquals(32, heldOut.scenarioCount());
    assertNotEquals(dev.datasetSha256(), heldOut.datasetSha256());
    assertTrue(dev.failures().isEmpty(), String.join("\n", dev.failures()));
    assertTrue(heldOut.failures().isEmpty(), String.join("\n", heldOut.failures()));
    assertNoMismatches(dev);
    assertNoMismatches(heldOut);

    BenchmarkMetrics deterministic =
        heldOut.metrics().get(BenchmarkMode.DETERMINISTIC_ONLY.name());
    assertNotNull(deterministic);
    assertEquals(16, deterministic.truePositives());
    assertEquals(0, deterministic.falseNegatives());
    assertEquals(2, deterministic.falsePositives());
    assertEquals(14, deterministic.trueNegatives());
    assertEquals(1.0, deterministic.recall(), DELTA);
    assertEquals(16.0 / 18.0, deterministic.precision(), DELTA);
    assertEquals(32.0 / 34.0, deterministic.f1(), DELTA);
    assertEquals(2.0 / 16.0, deterministic.falsePositiveRate(), DELTA);
    assertEquals(30.0 / 32.0, deterministic.classificationAccuracy(), DELTA);
    assertEquals(0, deterministic.legitimateStopped());
    assertEquals(2, deterministic.legitimateVerified());

    BenchmarkMetrics validatedAi = heldOut.metrics().get(BenchmarkMode.VALIDATED_AI.name());
    assertNotNull(validatedAi);
    assertEquals(16, validatedAi.truePositives());
    assertEquals(0, validatedAi.falseNegatives());
    assertEquals(2, validatedAi.falsePositives());
    assertEquals(14, validatedAi.trueNegatives());
    assertEquals(1.0, validatedAi.recall(), DELTA);
    assertEquals(32.0 / 34.0, validatedAi.f1(), DELTA);

    for (BenchmarkMode mode : BenchmarkMode.values()) {
      ThresholdAnalysis thresholds = heldOut.thresholdAnalysis().get(mode.name());
      assertNotNull(thresholds);
      assertEquals(1, thresholds.comparisons().size());
      assertEquals("production-baseline", thresholds.comparisons().get(0).configuration());
      assertTrue(thresholds.comparisons().get(0).baseline());
      assertTrue(thresholds.best().isEmpty());

      List<String> recommendations = heldOut.recommendations().get(mode.name());
      assertNotNull(recommendations);
      assertEquals(1, recommendations.size());
      assertTrue(recommendations.get(0).contains("fixed production baseline"));
      assertTrue(recommendations.get(0).contains("optimization"));
    }

    BenchmarkResult hardCase =
        result(heldOut, BenchmarkMode.DETERMINISTIC_ONLY, "HF16-paypal-known-urgent-payment");
    assertEquals(60, hardCase.actualScore());
    assertEquals(com.guardpay.risk.RiskLevel.MEDIUM, hardCase.actualLevel());
    assertEquals(com.guardpay.risk.RecommendedAction.VERIFY, hardCase.actualAction());
    assertEquals(
        List.of("URGENT_LANGUAGE", "KNOWN_RECIPIENT_SCAM"), hardCase.actualSignals());

    BenchmarkResult officialLink =
        result(heldOut, BenchmarkMode.DETERMINISTIC_ONLY, "HL09-paypal-official-link");
    assertEquals(0, officialLink.actualScore());
    assertFalse(officialLink.actualSignals().contains("SUSPICIOUS_URL"));

    BenchmarkResult aiEscalation =
        result(heldOut, BenchmarkMode.VALIDATED_AI, "HF07-paypal-invoice-known-ai-impersonation");
    assertEquals(65, aiEscalation.actualScore());
    assertEquals(45, result(
            heldOut,
            BenchmarkMode.DETERMINISTIC_ONLY,
            "HF07-paypal-invoice-known-ai-impersonation")
        .actualScore());

    BenchmarkComparison comparison = BenchmarkComparison.from(dev, heldOut);
    assertEquals(2, comparison.modes().size());
    assertEquals(dev.datasetSha256(), comparison.devDatasetSha256());
    assertEquals(heldOut.datasetSha256(), comparison.heldOutDatasetSha256());
    assertEquals(
        -2.0,
        comparison
            .modes()
            .get(BenchmarkMode.DETERMINISTIC_ONLY.name())
            .deltas()
            .get("falseNegatives"),
        DELTA);

    new BenchmarkReportWriter().writeComparison(comparison, OUTPUT);
    assertTrue(Files.exists(OUTPUT.resolve("comparison.json")));
    assertTrue(Files.exists(OUTPUT.resolve("comparison.md")));
    String comparisonMarkdown = Files.readString(OUTPUT.resolve("comparison.md"));
    assertTrue(comparisonMarkdown.contains("Dev vs Held-Out"));
    assertTrue(comparisonMarkdown.contains("Delta (held-out - dev)"));
  }

  @Test
  void devAndHeldOutDatasetsDoNotShareIdsOrExactInputs() throws IOException {
    List<BenchmarkScenario> dev = new BenchmarkDatasetLoader().load(DEV_DATASET);
    List<BenchmarkScenario> heldOut = new BenchmarkDatasetLoader().load(HELD_OUT_DATASET);

    Set<String> devIds =
        dev.stream().map(BenchmarkScenario::id).collect(Collectors.toSet());
    Set<String> heldOutIds =
        heldOut.stream().map(BenchmarkScenario::id).collect(Collectors.toSet());
    assertTrue(java.util.Collections.disjoint(devIds, heldOutIds));

    Set<String> devInputs =
        dev.stream()
            .map(scenario -> scenario.input().toString())
            .collect(Collectors.toCollection(HashSet::new));
    List<String> duplicateInputs =
        heldOut.stream()
            .filter(scenario -> devInputs.contains(scenario.input().toString()))
            .map(BenchmarkScenario::id)
            .toList();
    assertTrue(duplicateInputs.isEmpty(), "Held-out inputs overlap dev: " + duplicateInputs);

    assertTrue(heldOut.stream().allMatch(scenario -> scenario.id().startsWith("H")));
  }

  private static void assertNoMismatches(BenchmarkReport report) {
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
  }

  private static BenchmarkResult result(
      BenchmarkReport report, BenchmarkMode mode, String scenarioId) {
    return report.results().get(mode.name()).stream()
        .filter(result -> result.scenarioId().equals(scenarioId))
        .findFirst()
        .orElseThrow();
  }
}
