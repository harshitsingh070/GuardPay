package com.guardpay.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  public void writeComparison(BenchmarkComparison comparison, Path outputDirectory)
      throws IOException {
    Objects.requireNonNull(comparison, "comparison must not be null");
    Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
    Files.createDirectories(outputDirectory);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(outputDirectory.resolve("comparison.json").toFile(), comparison);
    Files.writeString(
        outputDirectory.resolve("comparison.md"),
        comparisonMarkdown(comparison),
        StandardCharsets.UTF_8);
  }

  private static String comparisonMarkdown(BenchmarkComparison comparison) {
    StringBuilder out = new StringBuilder();
    out.append("# GuardPay Dev vs Held-Out Benchmark Comparison\n\n");
    out.append("- Generated at: `").append(comparison.generatedAt()).append("`\n");
    out.append("- Dev dataset: `").append(comparison.devDataset()).append("`\n");
    out.append("- Dev SHA-256: `").append(comparison.devDatasetSha256()).append("`\n");
    out.append("- Held-out dataset: `").append(comparison.heldOutDataset()).append("`\n");
    out.append("- Held-out SHA-256: `").append(comparison.heldOutDatasetSha256()).append("`\n");
    out.append(
        "- Policy: held-out evaluation is separate and fixed-policy; it is not used for "
            + "threshold selection or tuning.\n\n");

    for (BenchmarkComparison.ModeComparison modeComparison : comparison.modes().values()) {
      BenchmarkMetrics dev = modeComparison.dev();
      BenchmarkMetrics heldOut = modeComparison.heldOut();
      out.append("## ").append(modeComparison.mode().name()).append("\n\n");
      out.append("| Metric | Dev | Held-out | Delta (held-out - dev) |\n");
      out.append("|---|---:|---:|---:|\n");
      appendCountRow(out, "Scenarios", dev.totalScenarios(), heldOut.totalScenarios());
      appendCountRow(out, "TP", dev.truePositives(), heldOut.truePositives());
      appendCountRow(out, "FN", dev.falseNegatives(), heldOut.falseNegatives());
      appendCountRow(out, "FP", dev.falsePositives(), heldOut.falsePositives());
      appendCountRow(out, "TN", dev.trueNegatives(), heldOut.trueNegatives());
      appendRateRow(out, "Precision", dev.precision(), heldOut.precision());
      appendRateRow(out, "Recall", dev.recall(), heldOut.recall());
      appendRateRow(out, "F1", dev.f1(), heldOut.f1());
      appendRateRow(
          out, "False-positive rate", dev.falsePositiveRate(), heldOut.falsePositiveRate());
      appendRateRow(
          out,
          "Classification accuracy",
          dev.classificationAccuracy(),
          heldOut.classificationAccuracy());
      appendCountRow(
          out,
          "Legitimate payment friction",
          dev.legitimateVerified() + dev.legitimateStopped(),
          heldOut.legitimateVerified() + heldOut.legitimateStopped());
      out.append('\n');
    }
    return out.toString();
  }

  private static void appendCountRow(
      StringBuilder out, String label, int dev, int heldOut) {
    out.append("| ")
        .append(label)
        .append(" | ")
        .append(dev)
        .append(" | ")
        .append(heldOut)
        .append(" | ")
        .append(signedCount(heldOut - dev))
        .append(" |\n");
  }

  private static void appendRateRow(
      StringBuilder out, String label, Double dev, Double heldOut) {
    Double delta = dev == null || heldOut == null ? null : heldOut - dev;
    out.append("| ")
        .append(label)
        .append(" | ")
        .append(percent(dev))
        .append(" | ")
        .append(percent(heldOut))
        .append(" | ")
        .append(signedPercentagePoints(delta))
        .append(" |\n");
  }

  private static String signedCount(int value) {
    return String.format(Locale.ROOT, "%+d", value);
  }

  private static String signedPercentagePoints(Double value) {
    return value == null
        ? "n/a"
        : String.format(Locale.ROOT, "%+.2f pp", value * 100.0);
  }

  private static String markdown(BenchmarkReport report) {
    StringBuilder out = new StringBuilder();
    out.append("# GuardPay Fraud Benchmark Report\n\n");
    out.append("- Generated at: `").append(report.generatedAt()).append("`\n");
    out.append("- Dataset: `").append(report.dataset()).append("`\n");
    out.append("- Dataset role: `").append(report.datasetRole()).append("`\n");
    out.append("- Dataset SHA-256: `").append(report.datasetSha256()).append("`\n");
    out.append("- Scenarios: ").append(report.scenarioCount()).append("\n");
    out.append(
        "- Friction definition: legitimate `VERIFY` + legitimate `STOP`. "
            + "`VERIFY` and `STOP` are reported separately.\n\n");

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

    out.append("\n## Behavioral coverage\n\n");
    out.append(
        "| Configuration | TP | FN | FP | TN | Precision | Recall | F1 | FPR | Accuracy |\n");
    out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (Map.Entry<String, BenchmarkMetrics> entry : report.behavioralComparison().entrySet()) {
      BenchmarkMetrics m = entry.getValue();
      out.append("| ")
          .append(entry.getKey())
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

    appendRecipientComparisonSection(out, report.recipientComparison());

    for (BenchmarkMode mode : BenchmarkMode.values()) {
      appendCategorySection(
          out,
          "Category metrics — " + mode.name(),
          report.categories() == null ? null : report.categories().get(mode.name()),
          false);
      appendCategorySection(
          out,
          "Signal-dimension metrics — " + mode.name(),
          report.signalDimensions() == null
              ? null
              : report.signalDimensions().get(mode.name()),
          true);
      appendThresholdSection(
          out,
          mode,
          report.thresholdAnalysis() == null
              ? null
              : report.thresholdAnalysis().get(mode.name()));
    }

    out.append("\n## Recommendations\n\n");
    if (report.recommendations() == null || report.recommendations().isEmpty()) {
      out.append("None.\n");
    } else {
      for (BenchmarkMode mode : BenchmarkMode.values()) {
        List<String> recommendations = report.recommendations().get(mode.name());
        if (recommendations == null || recommendations.isEmpty()) {
          continue;
        }
        out.append("### ").append(mode.name()).append("\n\n");
        for (String recommendation : recommendations) {
          out.append("- ").append(escape(recommendation)).append("\n");
        }
        out.append('\n');
      }
    }

    out.append("## Failures\n\n");
    if (report.failures().isEmpty()) {
      out.append("None.\n");
    } else {
      for (String failure : report.failures()) {
        out.append("- ").append(escape(failure)).append("\n");
      }
    }

    out.append("\n## Scenario results\n\n");
    out.append(
        "| Mode | Scenario | Category | Label | Expected | Actual | Expected action | Actual action | Match |\n");
    out.append("|---|---|---|---|---|---|---|---|---|\n");
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
            .append(escape(result.category()))
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

  private static void appendRecipientComparisonSection(
      StringBuilder out, Map<String, RecipientComparison> comparisons) {
    out.append("\n## Recipient intelligence comparison\n\n");
    if (comparisons == null || comparisons.isEmpty()) {
      out.append("None.\n");
      return;
    }
    out.append(
        "| Configuration | Mode | Recipient intelligence | TP | FN | FP | TN | Precision | Recall | F1 | Accuracy | Recipient scenarios | New recipient detected | Known recipient false-new | Amount anomaly detected | Repeated recipient attempts detected | History coverage |\n");
    out.append(
        "|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (RecipientComparison comparison : comparisons.values()) {
      BenchmarkMetrics m = comparison.metrics();
      RecipientMetrics r = comparison.recipientMetrics();
      out.append("| ")
          .append(escape(comparison.configuration()))
          .append(" | ")
          .append(comparison.mode().name())
          .append(" | ")
          .append(comparison.recipientIntelligence() ? "yes" : "no")
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
          .append(percent(m.classificationAccuracy()))
          .append(" | ")
          .append(r.recipientScenarios())
          .append(" | ")
          .append(r.newRecipientDetected())
          .append("/")
          .append(r.newRecipientScenarios())
          .append(" | ")
          .append(r.knownRecipientFalseNew())
          .append("/")
          .append(r.knownRecipientScenarios())
          .append(" | ")
          .append(r.amountAnomalyDetected())
          .append("/")
          .append(r.amountAnomalyScenarios())
          .append(" | ")
          .append(r.repeatedRecipientAttemptDetected())
          .append("/")
          .append(r.repeatedRecipientAttemptScenarios())
          .append(" | ")
          .append(percent(r.historyCoverageRate()))
          .append(" |\n");
    }
  }

  private static void appendCategorySection(
      StringBuilder out,
      String title,
      Map<String, CategoryMetrics> categories,
      boolean crossCuttingNote) {
    out.append("\n## ").append(title).append("\n\n");
    if (categories == null || categories.isEmpty()) {
      out.append("None.\n");
      return;
    }
    out.append(
        "| Category | Total | Fraud | Legit | TP | FN | FP | TN | Precision | Recall | F1 | Accuracy | FNR | FPR | Fraud ALLOW | Fraud VERIFY | Fraud STOP | Legit ALLOW | Legit VERIFY | Legit STOP | Legit friction |\n");
    out.append(
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (CategoryMetrics m : categories.values()) {
      out.append("| ")
          .append(escape(m.category()))
          .append(" | ")
          .append(m.totalScenarios())
          .append(" | ")
          .append(m.fraudScenarios())
          .append(" | ")
          .append(m.legitimateScenarios())
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
          .append(percent(m.accuracy()))
          .append(" | ")
          .append(percent(m.falseNegativeRate()))
          .append(" | ")
          .append(percent(m.falsePositiveRate()))
          .append(" | ")
          .append(m.fraudAllowed())
          .append(" | ")
          .append(m.fraudVerified())
          .append(" | ")
          .append(m.fraudStopped())
          .append(" | ")
          .append(m.legitimateAllowed())
          .append(" | ")
          .append(m.legitimateVerified())
          .append(" | ")
          .append(m.legitimateStopped())
          .append(" | ")
          .append(m.legitimatePaymentFriction())
          .append(" |\n");
    }
    if (crossCuttingNote) {
      out.append(
          "\nSignal dimensions are cross-cutting and may overlap the primary categories; "
              + "they are diagnostic views, not additive partitions.\n");
    }
  }

  private static void appendThresholdSection(
      StringBuilder out, BenchmarkMode mode, ThresholdAnalysis analysis) {
    out.append("\n## Threshold analysis — ").append(mode.name()).append("\n\n");
    if (analysis == null || analysis.comparisons().isEmpty()) {
      out.append("None.\n");
      return;
    }
    out.append(
        "| Configuration | Baseline | LOW max | MEDIUM max | HIGH max | TP | FN | FP | TN | Precision | Recall | F1 | Accuracy | FNR | FPR | Fraud ALLOW | Fraud VERIFY | Fraud STOP | Legit ALLOW | Legit VERIFY | Legit STOP | Legit friction |\n");
    out.append(
        "|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (ThresholdComparison c : analysis.comparisons()) {
      out.append("| ")
          .append(escape(c.configuration()))
          .append(" | ")
          .append(c.baseline() ? "yes" : "no")
          .append(" | ")
          .append(c.lowMax())
          .append(" | ")
          .append(c.mediumMax())
          .append(" | ")
          .append(c.highMax())
          .append(" | ")
          .append(c.truePositives())
          .append(" | ")
          .append(c.falseNegatives())
          .append(" | ")
          .append(c.falsePositives())
          .append(" | ")
          .append(c.trueNegatives())
          .append(" | ")
          .append(percent(c.precision()))
          .append(" | ")
          .append(percent(c.recall()))
          .append(" | ")
          .append(percent(c.f1()))
          .append(" | ")
          .append(percent(c.accuracy()))
          .append(" | ")
          .append(percent(c.falseNegativeRate()))
          .append(" | ")
          .append(percent(c.falsePositiveRate()))
          .append(" | ")
          .append(c.fraudAllowed())
          .append(" | ")
          .append(c.fraudVerified())
          .append(" | ")
          .append(c.fraudStopped())
          .append(" | ")
          .append(c.legitimateAllowed())
          .append(" | ")
          .append(c.legitimateVerified())
          .append(" | ")
          .append(c.legitimateStopped())
          .append(" | ")
          .append(c.legitimatePaymentFriction())
          .append(" |\n");
    }

    out.append("\nBest by metric:\n\n");
    if (analysis.best().isEmpty()) {
      out.append(
          "Not computed. Held-out evaluation uses a fixed policy and does not rank "
              + "alternative thresholds.\n");
      return;
    }
    for (Map.Entry<String, String> entry : analysis.best().entrySet()) {
      out.append("- `").append(entry.getKey()).append("`: `").append(entry.getValue()).append("`\n");
    }
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
