package com.guardpay.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.behavior.highvalue.FirstTimeHighValueAssessment;
import com.guardpay.behavior.highvalue.FirstTimeHighValueClassification;
import com.guardpay.behavior.highvalue.FirstTimeHighValueConfig;
import com.guardpay.behavior.highvalue.FirstTimeHighValueDetectionEngine;
import com.guardpay.recipient.Recipient;
import com.guardpay.transaction.Transaction;
import com.guardpay.transaction.TransactionStatus;
import com.guardpay.user.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Controlled high-value benchmark: every scenario is evaluated with the engine
 * enabled and disabled, using the same synthetic history and thresholds.
 * Metrics are detection-level (did the engine emit a high-value finding?), not
 * final risk-decision metrics; the deterministic engine remains the decision
 * authority.
 */
public final class HighValueBenchmarkRunner {

  private static final String ENABLED = "WITH_HIGH_VALUE";
  private static final String DISABLED = "WITHOUT_HIGH_VALUE";
  private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
  private static final UUID ESTABLISHED_RECIPIENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000012");
  private static final UUID NEW_RECIPIENT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000013");

  private final ObjectMapper mapper = new ObjectMapper();

  public HighValueBenchmarkReport run(Path datasetPath, Path outputDirectory) throws IOException {
    List<HighValueBenchmarkScenario> scenarios = load(datasetPath);
    List<HighValueEvaluation> enabled = new ArrayList<>();
    List<HighValueEvaluation> disabled = new ArrayList<>();
    for (HighValueBenchmarkScenario scenario : scenarios) {
      enabled.add(evaluate(scenario, true));
      disabled.add(evaluate(scenario, false));
    }

    Map<String, HighValueMetrics> metrics = new LinkedHashMap<>();
    metrics.put(ENABLED, HighValueMetrics.from(enabled));
    metrics.put(DISABLED, HighValueMetrics.from(disabled));

    HighValueBenchmarkReport report =
        new HighValueBenchmarkReport(
            OffsetDateTime.now(ZoneOffset.UTC).toString(),
            datasetPath.toString(),
            sha256(datasetPath),
            scenarios.size(),
            metrics,
            List.copyOf(enabled),
            List.copyOf(disabled));

    write(report, outputDirectory);
    return report;
  }

  private HighValueEvaluation evaluate(
      HighValueBenchmarkScenario scenario, boolean engineEnabled) {
    HighValueBenchmarkScenario.Input input = scenario.input();
    FirstTimeHighValueConfig config = FirstTimeHighValueConfig.defaults();
    config.setEnabled(engineEnabled);
    FirstTimeHighValueDetectionEngine engine = new FirstTimeHighValueDetectionEngine(config);

    User user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    Recipient established = recipient(ESTABLISHED_RECIPIENT_ID, user, "Established payee");
    Recipient newPayee = recipient(NEW_RECIPIENT_ID, user, "New payee");

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String historyCurrency =
        input.historyCurrency() != null ? input.historyCurrency() : input.currency();
    List<Transaction> history = new ArrayList<>();
    List<BigDecimal> oldAmounts = input.historyAmounts() == null ? List.of() : input.historyAmounts();
    int oldCount = oldAmounts.size();
    for (int i = 0; i < oldCount; i++) {
      long daysAgo = oldCount <= 1 ? 80 : 80 - (i * 70L / (oldCount - 1));
      history.add(
          transaction(user, established, historyCurrency, oldAmounts.get(i), now.minusDays(daysAgo)));
    }
    List<BigDecimal> recentAmounts =
        input.recentAmounts() == null ? List.of() : input.recentAmounts();
    for (int i = 0; i < recentAmounts.size(); i++) {
      history.add(
          transaction(
              user,
              established,
              historyCurrency,
              recentAmounts.get(i),
              now.minusDays(Math.max(1, recentAmounts.size() - i))));
    }

    UUID recipientId = input.newRecipient() ? newPayee.getId() : established.getId();
    long startedAt = System.nanoTime();
    FirstTimeHighValueAssessment assessment =
        engine.evaluate(
            USER_ID,
            input.amount(),
            input.currency(),
            recipientId,
            history,
            null,
            now,
            oldCount + recentAmounts.size() >= 20 ? "ESTABLISHED" : "INITIALIZING");
    long latency = System.nanoTime() - startedAt;

    return new HighValueEvaluation(
        scenario.id(),
        scenario.category(),
        scenario.expectedHighValue(),
        assessment.detected(),
        assessment.classification().name(),
        assessment.severity(),
        assessment.amountRatio(),
        assessment.baselineWindow(),
        assessment.classification() == FirstTimeHighValueClassification.COLD_START_INSUFFICIENT_DATA,
        latency);
  }

  private List<HighValueBenchmarkScenario> load(Path datasetPath) throws IOException {
    List<HighValueBenchmarkScenario> scenarios = new ArrayList<>();
    try (BufferedReader reader =
        Files.newBufferedReader(datasetPath, StandardCharsets.UTF_8)) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.isBlank()) {
          continue;
        }
        try {
          scenarios.add(mapper.readValue(line, HighValueBenchmarkScenario.class));
        } catch (IOException ex) {
          throw new IllegalArgumentException(
              "Invalid high-value benchmark JSON at " + datasetPath + ":" + lineNumber, ex);
        }
      }
    }
    if (scenarios.isEmpty()) {
      throw new IllegalArgumentException("High-value benchmark dataset is empty: " + datasetPath);
    }
    return List.copyOf(scenarios);
  }

  private static Recipient recipient(UUID id, User user, String name) {
    Recipient recipient = new Recipient();
    ReflectionTestUtils.setField(recipient, "id", id);
    recipient.setUser(user);
    recipient.setName(name);
    return recipient;
  }

  private static Transaction transaction(
      User user,
      Recipient recipient,
      String currency,
      BigDecimal amount,
      OffsetDateTime timestamp) {
    Transaction transaction = new Transaction();
    ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
    transaction.setUser(user);
    transaction.setRecipient(recipient);
    transaction.setCurrency(currency);
    transaction.setAmount(amount);
    transaction.setStatus(TransactionStatus.COMPLETED);
    transaction.setTransactionTimestamp(timestamp);
    return transaction;
  }

  private void write(HighValueBenchmarkReport report, Path outputDirectory) throws IOException {
    Files.createDirectories(outputDirectory);
    mapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(outputDirectory.resolve("high-value-report.json").toFile(), report);
    Files.writeString(
        outputDirectory.resolve("high-value-report.md"),
        markdown(report),
        StandardCharsets.UTF_8);
  }

  private static String markdown(HighValueBenchmarkReport report) {
    StringBuilder out = new StringBuilder();
    out.append("# GuardPay First-Time High-Value Benchmark\n\n");
    out.append("- Generated at: `").append(report.generatedAt()).append("`\n");
    out.append("- Dataset: `").append(report.dataset()).append("`\n");
    out.append("- Dataset SHA-256: `").append(report.datasetSha256()).append("`\n");
    out.append("- Scenarios: ").append(report.scenarioCount()).append("\n");
    out.append(
        "- Detection metric definition: a scenario is positive when the engine emits a "
            + "high-value finding. This is not the final payment decision.\n\n");

    out.append("## Engine comparison\n\n");
    out.append(
        "| Configuration | Total | TP | FN | FP | TN | Precision | Recall | F1 | FPR | High-value findings | Cold starts | Avg ms | P50 ms | P95 ms |\n");
    out.append(
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
    for (Map.Entry<String, HighValueMetrics> entry : report.metrics().entrySet()) {
      HighValueMetrics m = entry.getValue();
      out.append("| ")
          .append(entry.getKey())
          .append(" | ")
          .append(m.totalScenarios())
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
          .append(m.highValueFindings())
          .append(" | ")
          .append(m.coldStartCount())
          .append(" | ")
          .append(decimal(m.averageLatencyMs()))
          .append(" | ")
          .append(decimal(m.p50LatencyMs()))
          .append(" | ")
          .append(decimal(m.p95LatencyMs()))
          .append(" |\n");
    }

    out.append("\n## Scenario results\n\n");
    out.append(
        "| Configuration | Scenario | Category | Expected | Detected | Classification | Severity | Amount ratio | Baseline | Cold start |\n");
    out.append("|---|---|---|---|---|---|---|---:|---|---|\n");
    appendScenarioRows(out, ENABLED, report.withHighValue());
    appendScenarioRows(out, DISABLED, report.withoutHighValue());
    return out.toString();
  }

  private static void appendScenarioRows(
      StringBuilder out, String configuration, List<HighValueEvaluation> evaluations) {
    for (HighValueEvaluation e : evaluations) {
      out.append("| ")
          .append(configuration)
          .append(" | ")
          .append(escape(e.scenarioId()))
          .append(" | ")
          .append(escape(e.category()))
          .append(" | ")
          .append(e.expectedHighValue())
          .append(" | ")
          .append(e.detected())
          .append(" | ")
          .append(e.classification())
          .append(" | ")
          .append(e.severity() == null ? "" : e.severity())
          .append(" | ")
          .append(e.amountRatio() == null ? "" : e.amountRatio())
          .append(" | ")
          .append(e.baselineWindow() == null ? "" : e.baselineWindow())
          .append(" | ")
          .append(e.coldStart())
          .append(" |\n");
    }
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

  private static String percent(Double value) {
    return value == null ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
  }

  private static String decimal(double value) {
    return String.format(Locale.ROOT, "%.3f", value);
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
  }

  public record HighValueBenchmarkReport(
      String generatedAt,
      String dataset,
      String datasetSha256,
      int scenarioCount,
      Map<String, HighValueMetrics> metrics,
      List<HighValueEvaluation> withHighValue,
      List<HighValueEvaluation> withoutHighValue) {}

  public record HighValueEvaluation(
      String scenarioId,
      String category,
      boolean expectedHighValue,
      boolean detected,
      String classification,
      String severity,
      BigDecimal amountRatio,
      String baselineWindow,
      boolean coldStart,
      long latencyNanos) {}

  public record HighValueMetrics(
      int totalScenarios,
      int truePositives,
      int falseNegatives,
      int falsePositives,
      int trueNegatives,
      Double precision,
      Double recall,
      Double f1,
      Double falsePositiveRate,
      int highValueFindings,
      int coldStartCount,
      double averageLatencyMs,
      double p50LatencyMs,
      double p95LatencyMs) {

    static HighValueMetrics from(List<HighValueEvaluation> evaluations) {
      int tp = 0;
      int fn = 0;
      int fp = 0;
      int tn = 0;
      int findings = 0;
      int coldStarts = 0;
      List<Double> latencies = new ArrayList<>();
      for (HighValueEvaluation evaluation : evaluations) {
        if (evaluation.detected()) {
          findings++;
        }
        if (evaluation.coldStart()) {
          coldStarts++;
        }
        if (evaluation.expectedHighValue()) {
          if (evaluation.detected()) {
            tp++;
          } else {
            fn++;
          }
        } else if (evaluation.detected()) {
          fp++;
        } else {
          tn++;
        }
        latencies.add(evaluation.latencyNanos() / 1_000_000.0);
      }
      Double precision = MetricMath.ratio(tp, tp + fp);
      Double recall = MetricMath.ratio(tp, tp + fn);
      latencies.sort(Double::compareTo);
      return new HighValueMetrics(
          evaluations.size(),
          tp,
          fn,
          fp,
          tn,
          precision,
          recall,
          MetricMath.f1(precision, recall),
          MetricMath.ratio(fp, fp + tn),
          findings,
          coldStarts,
          latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
          MetricMath.percentile(latencies, 50),
          MetricMath.percentile(latencies, 95));
    }
  }
}
