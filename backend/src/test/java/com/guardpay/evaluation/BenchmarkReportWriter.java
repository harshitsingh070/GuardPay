package com.guardpay.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Writes machine-readable JSON and a human-readable Markdown benchmark report. */
public final class BenchmarkReportWriter {

  private final ObjectMapper mapper = new ObjectMapper();

  public void write(BenchmarkReport report, Path outputDirectory) throws IOException {
    Objects.requireNonNull(report, "report must not be null");
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    Files.createDirectories(outputDirectory);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(outputDirectory.resolve("report.json").toFile(), report);
    Files.writeString(
        outputDirectory.resolve("report.md"), markdown(report), StandardCharsets.UTF_8);
  }

  private static String markdown(BenchmarkReport report) {
    StringBuilder out = new StringBuilder();
    out.append("# GuardPay Fraud Benchmark Report\n\n");
    out.append("- Generated at: `").append(report.generatedAt()).append("`\n");
    out.append("- Dataset: `").append(report.dataset()).append("`\n");
    out.append("- Scenarios: ").append(report.scenarioCount()).append("\n\n");

    out.append("## Mode summary\n\n");
    out.append(
        "| Mode | Scenarios | Evaluated | Failed | TP | FN | FP | TN | Precision | Recall | F1 | FPR | Accuracy | Exact score | Exact level | Exact action |\n");
    out.append(
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (BenchmarkMode mode : BenchmarkMode.values()) {
      BenchmarkMetrics m = report.metrics().get(mode.name());
      if (m == null) {
        continue;
      }
      out.append("| ")
          .append(mode.name())
          .append(" | ")
          .append(m.totalScenarios())
          .append(" | ")
          .append(m.evaluatedScenarios())
          .append(" | ")
          .append(m.failedScenarios())
          .append(" | ")
          .append(m.truePositives())
          .append(" | ")
          .append(m.falseNegatives())
          .append(" | ")
          .append(m.falsePositives())
          .append(" | ")
          .append(m.trueNegatives())
          .append(" | ")
          .append(percent(m.precision()))
          .append(" | ")
          .append(percent(m.recall()))
          .append(" | ")
          .append(percent(m.f1()))
          .append(" | ")
          .append(percent(m.falsePositiveRate()))
          .append(" | ")
          .append(percent(m.classificationAccuracy()))
          .append(" | ")
          .append(percent(m.exactScoreAccuracy()))
          .append(" | ")
          .append(percent(m.exactLevelAccuracy()))
          .append(" | ")
          .append(percent(m.exactActionAccuracy()))
          .append(" |\n");
    }

    out.append("\n## Actions and signal quality\n\n");
    out.append(
        "| Mode | ALLOW | VERIFY | STOP | Fraud detected | Fraud missed | Legit allowed | Legit verified | Legit stopped | Signal TP | Signal FP | Signal FN | Signal precision | Signal recall | Signal F1 | Avg ms | P50 ms | P95 ms |\n");
    out.append(
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (BenchmarkMode mode : BenchmarkMode.values()) {
      BenchmarkMetrics m = report.metrics().get(mode.name());
      if (m == null) {
        continue;
      }
      out.append("| ")
          .append(mode.name())
          .append(" | ")
          .append(m.allowCount())
          .append(" | ")
          .append(m.verifyCount())
          .append(" | ")
          .append(m.stopCount())
          .append(" | ")
          .append(m.fraudDetected())
          .append(" | ")
          .append(m.fraudMissed())
          .append(" | ")
          .append(m.legitimateAllowed())
          .append(" | ")
          .append(m.legitimateVerified())
          .append(" | ")
          .append(m.legitimateStopped())
          .append(" | ")
          .append(m.signalTruePositives())
          .append(" | ")
          .append(m.signalFalsePositives())
          .append(" | ")
          .append(m.signalFalseNegatives())
          .append(" | ")
          .append(percent(m.signalPrecision()))
          .append(" | ")
          .append(percent(m.signalRecall()))
          .append(" | ")
          .append(percent(m.signalF1()))
          .append(" | ")
          .append(decimal(m.averageLatencyMs()))
          .append(" | ")
          .append(decimal(m.p50LatencyMs()))
          .append(" | ")
          .append(decimal(m.p95LatencyMs()))
          .append(" |\n");
    }

    out.append("\n## Failures\n\n");
    if (report.failures().isEmpty()) {
      out.append("None.\n");
    } else {
      for (String failure : report.failures()) {
        out.append("- ").append(escape(failure)).append("\n");
      }
    }

    out.append("\n## Scenario results\n\n");
    out.append(
        "| Mode | Scenario | Label | Expected | Actual | Expected action | Actual action | Match |\n");
    out.append("|---|---|---|---|---|---|---|---|\n");
    for (BenchmarkMode mode : BenchmarkMode.values()) {
      List<BenchmarkResult> results = report.results().get(mode.name());
      if (results == null) {
        continue;
      }
      for (BenchmarkResult result : results) {
        boolean match =
            result.scoreMatch()
                && result.levelMatch()
                && result.actionMatch()
                && result.signalsMatch();
        out.append("| ")
            .append(mode.name())
            .append(" | ")
            .append(escape(result.scenarioId()))
            .append(" | ")
            .append(result.label())
            .append(" | ")
            .append(result.expectedScore())
            .append(' ')
            .append(result.expectedLevel())
            .append(" | ")
            .append(decision(result.actualScore(), result.actualLevel(), result.actualAction()))
            .append(" | ")
            .append(result.expectedAction())
            .append(" | ")
            .append(result.actualAction() == null ? "ERROR" : result.actualAction())
            .append(" | ")
            .append(match ? "yes" : "no")
            .append(" |\n");
      }
    }

    return out.toString();
  }

  private static String decision(
      Integer score,
      com.guardpay.risk.RiskLevel level,
      com.guardpay.risk.RecommendedAction action) {
    if (score == null) {
      return "ERROR";
    }
    return score + " " + level + " " + action;
  }

  private static String percent(Double value) {
    return value == null ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
  }

  private static String decimal(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
  }
}
