package com.guardpay.evaluation;

import com.guardpay.risk.RecommendedAction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BenchmarkMetrics(
    int totalScenarios,
    int evaluatedScenarios,
    int failedScenarios,
    int fraudScenarios,
    int legitimateScenarios,
    int truePositives,
    int falseNegatives,
    int falsePositives,
    int trueNegatives,
    int fraudDetected,
    int fraudMissed,
    int legitimateAllowed,
    int legitimateVerified,
    int legitimateStopped,
    int allowCount,
    int verifyCount,
    int stopCount,
    Double precision,
    Double recall,
    Double f1,
    Double falsePositiveRate,
    Double classificationAccuracy,
    Double exactScoreAccuracy,
    Double exactLevelAccuracy,
    Double exactActionAccuracy,
    int signalTruePositives,
    int signalFalsePositives,
    int signalFalseNegatives,
    Double signalPrecision,
    Double signalRecall,
    Double signalF1,
    double averageLatencyMs,
    double p50LatencyMs,
    double p95LatencyMs) {

  public static BenchmarkMetrics from(List<BenchmarkResult> allResults) {
    List<BenchmarkResult> results = allResults.stream().filter(BenchmarkResult::evaluated).toList();

    int fraud = 0;
    int legitimate = 0;
    int tp = 0;
    int fn = 0;
    int fp = 0;
    int tn = 0;
    int fraudDetected = 0;
    int fraudMissed = 0;
    int legitimateAllowed = 0;
    int legitimateVerified = 0;
    int legitimateStopped = 0;
    int allowCount = 0;
    int verifyCount = 0;
    int stopCount = 0;
    int exactScore = 0;
    int exactLevel = 0;
    int exactAction = 0;
    int signalTp = 0;
    int signalFp = 0;
    int signalFn = 0;
    List<Double> latencies = new ArrayList<>();

    for (BenchmarkResult result : results) {
      RecommendedAction actual = result.actualAction();
      if (actual == RecommendedAction.ALLOW) {
        allowCount++;
      } else if (actual == RecommendedAction.VERIFY) {
        verifyCount++;
      } else if (actual == RecommendedAction.STOP) {
        stopCount++;
      }

      if (result.label() == BenchmarkLabel.FRAUD) {
        fraud++;
        if (actual == RecommendedAction.ALLOW) {
          fn++;
          fraudMissed++;
        } else {
          tp++;
          fraudDetected++;
        }
      } else {
        legitimate++;
        if (actual == RecommendedAction.ALLOW) {
          tn++;
          legitimateAllowed++;
        } else {
          fp++;
          if (actual == RecommendedAction.VERIFY) {
            legitimateVerified++;
          } else if (actual == RecommendedAction.STOP) {
            legitimateStopped++;
          }
        }
      }

      if (result.scoreMatch()) {
        exactScore++;
      }
      if (result.levelMatch()) {
        exactLevel++;
      }
      if (result.actionMatch()) {
        exactAction++;
      }

      Set<String> expectedSignals = new HashSet<>(result.expectedSignals());
      Set<String> actualSignals = new HashSet<>(result.actualSignals());
      for (String expected : expectedSignals) {
        if (actualSignals.contains(expected)) {
          signalTp++;
        } else {
          signalFn++;
        }
      }
      for (String actualSignal : actualSignals) {
        if (!expectedSignals.contains(actualSignal)) {
          signalFp++;
        }
      }

      latencies.add(result.latencyNanos() / 1_000_000.0);
    }

    int evaluated = results.size();
    Double precision = ratio(tp, tp + fp);
    Double recall = ratio(tp, tp + fn);
    Double signalPrecision = ratio(signalTp, signalTp + signalFp);
    Double signalRecall = ratio(signalTp, signalTp + signalFn);

    latencies.sort(Double::compareTo);

    return new BenchmarkMetrics(
        allResults.size(),
        evaluated,
        allResults.size() - evaluated,
        fraud,
        legitimate,
        tp,
        fn,
        fp,
        tn,
        fraudDetected,
        fraudMissed,
        legitimateAllowed,
        legitimateVerified,
        legitimateStopped,
        allowCount,
        verifyCount,
        stopCount,
        precision,
        recall,
        f1(precision, recall),
        ratio(fp, fp + tn),
        ratio(tp + tn, evaluated),
        ratio(exactScore, evaluated),
        ratio(exactLevel, evaluated),
        ratio(exactAction, evaluated),
        signalTp,
        signalFp,
        signalFn,
        signalPrecision,
        signalRecall,
        f1(signalPrecision, signalRecall),
        latencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0),
        percentile(latencies, 50),
        percentile(latencies, 95));
  }

  private static Double ratio(int numerator, int denominator) {
    return denominator == 0 ? null : (double) numerator / denominator;
  }

  private static Double f1(Double precision, Double recall) {
    if (precision == null || recall == null || precision + recall == 0.0) {
      return null;
    }
    return 2 * precision * recall / (precision + recall);
  }

  private static double percentile(List<Double> values, double percentile) {
    if (values.isEmpty()) {
      return 0.0;
    }
    int index = (int) Math.ceil(percentile / 100.0 * values.size()) - 1;
    return values.get(Math.max(0, Math.min(index, values.size() - 1)));
  }
}
