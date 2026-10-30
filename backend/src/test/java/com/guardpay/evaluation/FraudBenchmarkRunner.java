package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.RiskSignal;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Executes every scenario in both modes and writes JSON/Markdown reports. */
public final class FraudBenchmarkRunner {

  public BenchmarkReport run(Path datasetPath, Path outputDirectory) throws IOException {
    return run(datasetPath, outputDirectory, BenchmarkFilter.none(), BenchmarkDatasetRole.DEV);
  }

  public BenchmarkReport run(
      Path datasetPath, Path outputDirectory, BenchmarkDatasetRole datasetRole)
      throws IOException {
    return run(datasetPath, outputDirectory, BenchmarkFilter.none(), datasetRole);
  }

  public BenchmarkReport run(
      Path datasetPath, Path outputDirectory, BenchmarkFilter benchmarkFilter) throws IOException {
    return run(datasetPath, outputDirectory, benchmarkFilter, BenchmarkDatasetRole.DEV);
  }

  public BenchmarkReport run(
      Path datasetPath,
      Path outputDirectory,
      BenchmarkFilter benchmarkFilter,
      BenchmarkDatasetRole datasetRole)
      throws IOException {
    BenchmarkDatasetRole role = datasetRole == null ? BenchmarkDatasetRole.DEV : datasetRole;
    List<BenchmarkScenario> allScenarios = new BenchmarkDatasetLoader().load(datasetPath);
    BenchmarkFilter filter = benchmarkFilter == null ? BenchmarkFilter.none() : benchmarkFilter;
    List<BenchmarkScenario> scenarios =
        allScenarios.stream().filter(filter::matches).toList();
    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException(
          "Benchmark filter selected no scenarios from " + datasetPath);
    }

    String datasetSha256 = sha256(datasetPath);
    Map<String, BenchmarkMetrics> metrics = new LinkedHashMap<>();
    Map<String, Map<String, CategoryMetrics>> categories = new LinkedHashMap<>();
    Map<String, Map<String, CategoryMetrics>> signalDimensions = new LinkedHashMap<>();
    Map<String, RecipientComparison> recipientComparison = new LinkedHashMap<>();
    Map<String, ThresholdAnalysis> thresholdAnalysis = new LinkedHashMap<>();
    Map<String, List<String>> recommendations = new LinkedHashMap<>();
    Map<String, List<BenchmarkResult>> results = new LinkedHashMap<>();
    List<String> failures = new ArrayList<>();
    List<ThresholdConfiguration> thresholdConfigurations = ThresholdConfiguration.defaults();

    for (BenchmarkMode mode : BenchmarkMode.values()) {
      List<BenchmarkResult> modeResults = runMode(scenarios, mode, true, failures);
      results.put(mode.name(), List.copyOf(modeResults));

      BenchmarkMetrics modeMetrics = BenchmarkMetrics.from(modeResults);
      metrics.put(mode.name(), modeMetrics);

      List<BenchmarkResult> baselineResults = runMode(scenarios, mode, false, failures);
      recipientComparison.put(
          mode.name() + "_BASELINE",
          new RecipientComparison(
              mode.name() + "_BASELINE",
              mode,
              false,
              BenchmarkMetrics.from(baselineResults),
              RecipientMetrics.from(scenarios, baselineResults)));
      recipientComparison.put(
          mode.name() + "_RECIPIENT",
          new RecipientComparison(
              mode.name() + "_RECIPIENT",
              mode,
              true,
              modeMetrics,
              RecipientMetrics.from(scenarios, modeResults)));

      Map<String, CategoryMetrics> modeCategories = categoryMetrics(modeResults);
      categories.put(mode.name(), modeCategories);

      Map<String, CategoryMetrics> modeSignalDimensions =
          signalDimensionMetrics(scenarios, modeResults, mode);
      signalDimensions.put(mode.name(), modeSignalDimensions);

      ThresholdAnalysis modeThresholds =
          role.thresholdOptimizationAllowed()
              ? ThresholdAnalysis.from(mode, modeResults, thresholdConfigurations)
              : ThresholdAnalysis.fixed(
                  mode, modeResults, ThresholdConfiguration.production());
      thresholdAnalysis.put(mode.name(), modeThresholds);

      recommendations.put(
          mode.name(),
          role.thresholdOptimizationAllowed()
              ? BenchmarkRecommendations.generate(
                  mode, modeMetrics, modeCategories, modeThresholds)
              : List.of(
                  "Held-out evaluation uses only the fixed production baseline; "
                      + "threshold optimization and best-metric selection are disabled."));
    }

    BenchmarkReport report =
        new BenchmarkReport(
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            datasetPath.toString(),
            datasetSha256,
            role,
            scenarios.size(),
            immutable(metrics),
            immutable(categories),
            immutable(signalDimensions),
            immutable(recipientComparison),
            immutable(thresholdAnalysis),
            immutable(recommendations),
            immutable(results),
            List.copyOf(failures));

    new BenchmarkReportWriter().write(report, outputDirectory);
    return report;
  }

  private static Map<String, CategoryMetrics> categoryMetrics(List<BenchmarkResult> results) {
    Map<String, List<BenchmarkResult>> groupedResults = new TreeMap<>();
    for (BenchmarkResult result : results) {
      groupedResults
          .computeIfAbsent(result.category(), category -> new ArrayList<>())
          .add(result);
    }

    Map<String, CategoryMetrics> categoryMetrics = new LinkedHashMap<>();
    for (Map.Entry<String, List<BenchmarkResult>> entry : groupedResults.entrySet()) {
      categoryMetrics.put(
          entry.getKey(), CategoryMetrics.from(entry.getKey(), entry.getValue()));
    }
    return immutable(categoryMetrics);
  }

  private static Map<String, CategoryMetrics> signalDimensionMetrics(
      List<BenchmarkScenario> scenarios,
      List<BenchmarkResult> results,
      BenchmarkMode mode) {
    Map<String, Set<String>> scenarioIdsBySignal = new LinkedHashMap<>();
    for (RiskSignal signal : RiskSignal.values()) {
      scenarioIdsBySignal.put(signal.name(), new HashSet<>());
    }
    for (BenchmarkScenario scenario : scenarios) {
      for (String signal : expectedFor(scenario, mode).signals()) {
        Set<String> scenarioIds = scenarioIdsBySignal.get(signal);
        if (scenarioIds != null) {
          scenarioIds.add(scenario.id());
        }
      }
    }

    Map<String, List<BenchmarkResult>> groupedResults = new LinkedHashMap<>();
    for (String signal : scenarioIdsBySignal.keySet()) {
      groupedResults.put(signal, new ArrayList<>());
    }
    for (BenchmarkResult result : results) {
      for (Map.Entry<String, Set<String>> entry : scenarioIdsBySignal.entrySet()) {
        if (entry.getValue().contains(result.scenarioId())) {
          groupedResults.get(entry.getKey()).add(result);
        }
      }
    }

    Map<String, CategoryMetrics> dimensions = new LinkedHashMap<>();
    for (Map.Entry<String, List<BenchmarkResult>> entry : groupedResults.entrySet()) {
      dimensions.put(entry.getKey(), CategoryMetrics.from(entry.getKey(), entry.getValue()));
    }
    return immutable(dimensions);
  }

  private static List<BenchmarkResult> runMode(
      List<BenchmarkScenario> scenarios,
      BenchmarkMode mode,
      boolean recipientIntelligence,
      List<String> failures) {
    List<BenchmarkResult> results = new ArrayList<>();
    for (BenchmarkScenario scenario : scenarios) {
      Expected expected = expectedFor(scenario, mode);
      try {
        BenchmarkHarness.Result harnessResult =
            new BenchmarkHarness().evaluate(scenario, mode, recipientIntelligence);
        AutomaticEvaluationResponseDto response = harnessResult.response();
        List<String> actualSignals =
            response.findings().stream()
                .filter(RiskFindingDto::triggered)
                .map(RiskFindingDto::code)
                .toList();

        boolean scoreMatch = response.score() == expected.score();
        boolean levelMatch = response.riskLevel() == expected.level();
        boolean actionMatch = response.recommendedAction() == expected.action();
        boolean signalsMatch =
            new HashSet<>(actualSignals).equals(new HashSet<>(expected.signals()));

        results.add(
            new BenchmarkResult(
                scenario.id(),
                scenario.category(),
                scenario.label(),
                mode,
                expected.score(),
                expected.level(),
                expected.action(),
                expected.signals(),
                response.score(),
                response.riskLevel(),
                response.recommendedAction(),
                actualSignals,
                response.aiStatus(),
                harnessResult.latencyNanos(),
                scoreMatch,
                levelMatch,
                actionMatch,
                signalsMatch,
                null));
      } catch (RuntimeException ex) {
        String error = ex.getClass().getSimpleName() + ": " + safeMessage(ex);
        String configuration = mode.name() + (recipientIntelligence ? "" : "_BASELINE");
        failures.add(configuration + ":" + scenario.id() + ":" + error);
        results.add(
            new BenchmarkResult(
                scenario.id(),
                scenario.category(),
                scenario.label(),
                mode,
                expected.score(),
                expected.level(),
                expected.action(),
                expected.signals(),
                null,
                null,
                null,
                List.of(),
                null,
                0L,
                false,
                false,
                false,
                false,
                error));
      }
    }
    return List.copyOf(results);
  }

  private static Expected expectedFor(BenchmarkScenario scenario, BenchmarkMode mode) {
    if (mode == BenchmarkMode.VALIDATED_AI && scenario.expectedAi() != null) {
      BenchmarkScenario.ExpectedAi ai = scenario.expectedAi();
      return new Expected(ai.score(), ai.riskLevel(), ai.action(), ai.signals());
    }
    return new Expected(
        scenario.expectedScore(),
        scenario.expectedRiskLevel(),
        scenario.expectedAction(),
        scenario.expectedSignals());
  }

  private static String sha256(Path path) throws IOException {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static <K, V> Map<K, V> immutable(Map<K, V> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  private static String safeMessage(RuntimeException ex) {
    return ex.getMessage() == null ? "(no message)" : ex.getMessage();
  }

  private record Expected(
      int score, RiskLevel level, RecommendedAction action, List<String> signals) {}
}
