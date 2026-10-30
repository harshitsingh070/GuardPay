package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes every scenario in both modes and writes JSON/Markdown reports. */
public final class FraudBenchmarkRunner {

  public BenchmarkReport run(Path datasetPath, Path outputDirectory) throws IOException {
    return run(datasetPath, outputDirectory, BenchmarkFilter.none());
  }

  public BenchmarkReport run(
      Path datasetPath, Path outputDirectory, BenchmarkFilter benchmarkFilter) throws IOException {
    List<BenchmarkScenario> allScenarios = new BenchmarkDatasetLoader().load(datasetPath);
    BenchmarkFilter filter = benchmarkFilter == null ? BenchmarkFilter.none() : benchmarkFilter;
    List<BenchmarkScenario> scenarios =
        allScenarios.stream().filter(filter::matches).toList();
    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException(
          "Benchmark filter selected no scenarios from " + datasetPath);
    }
    Map<String, BenchmarkMetrics> metrics = new LinkedHashMap<>();
    Map<String, List<BenchmarkResult>> results = new LinkedHashMap<>();
    List<String> failures = new ArrayList<>();

    for (BenchmarkMode mode : BenchmarkMode.values()) {
      List<BenchmarkResult> modeResults = runMode(scenarios, mode, failures);
      results.put(mode.name(), List.copyOf(modeResults));
      metrics.put(mode.name(), BenchmarkMetrics.from(modeResults));
    }

    BenchmarkReport report =
        new BenchmarkReport(
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            datasetPath.toString(),
            scenarios.size(),
            java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metrics)),
            java.util.Collections.unmodifiableMap(new LinkedHashMap<>(results)),
            List.copyOf(failures));

    new BenchmarkReportWriter().write(report, outputDirectory);
    return report;
  }

  private static List<BenchmarkResult> runMode(
      List<BenchmarkScenario> scenarios, BenchmarkMode mode, List<String> failures) {
    List<BenchmarkResult> results = new ArrayList<>();
    for (BenchmarkScenario scenario : scenarios) {
      Expected expected = expectedFor(scenario, mode);
      try {
        BenchmarkHarness.Result harnessResult = new BenchmarkHarness().evaluate(scenario, mode);
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
        failures.add(mode.name() + ":" + scenario.id() + ":" + error);
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

  private static String safeMessage(RuntimeException ex) {
    return ex.getMessage() == null ? "(no message)" : ex.getMessage();
  }

  private record Expected(
      int score, RiskLevel level, RecommendedAction action, List<String> signals) {}
}
