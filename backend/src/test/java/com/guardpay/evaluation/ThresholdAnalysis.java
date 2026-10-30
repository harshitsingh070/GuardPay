package com.guardpay.evaluation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Threshold comparisons plus deterministic best-configuration labels. */
public record ThresholdAnalysis(
    List<ThresholdComparison> comparisons, Map<String, String> best) {

  public ThresholdAnalysis {
    comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
    best = best == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(best));
  }

  public static ThresholdAnalysis from(
      BenchmarkMode mode,
      List<BenchmarkResult> results,
      List<ThresholdConfiguration> configurations) {
    List<ThresholdComparison> comparisons =
        configurations.stream()
            .map(configuration -> ThresholdComparison.from(mode, results, configuration))
            .toList();

    Map<String, String> best = new LinkedHashMap<>();
    best.put("precision", bestBy(comparisons, ThresholdComparison::precision, true));
    best.put("recall", bestBy(comparisons, ThresholdComparison::recall, true));
    best.put("f1", bestBy(comparisons, ThresholdComparison::f1, true));
    best.put("accuracy", bestBy(comparisons, ThresholdComparison::accuracy, true));
    best.put(
        "falsePositiveRate",
        bestBy(comparisons, ThresholdComparison::falsePositiveRate, false));
    best.put(
        "falseNegativeRate",
        bestBy(comparisons, ThresholdComparison::falseNegativeRate, false));
    best.put(
        "fraudAllowed", bestBy(comparisons, comparison -> (double) comparison.fraudAllowed(), false));
    best.put(
        "legitimateVerified",
        bestBy(comparisons, comparison -> (double) comparison.legitimateVerified(), false));
    best.put(
        "legitimateStopped",
        bestBy(comparisons, comparison -> (double) comparison.legitimateStopped(), false));
    best.put(
        "legitimatePaymentFriction",
        bestBy(comparisons, comparison -> (double) comparison.legitimatePaymentFriction(), false));

    return new ThresholdAnalysis(comparisons, best);
  }

  private static String bestBy(
      List<ThresholdComparison> comparisons,
      Function<ThresholdComparison, Double> metric,
      boolean higherIsBetter) {
    ThresholdComparison best = null;
    Double bestValue = null;
    for (ThresholdComparison comparison : comparisons) {
      Double value = metric.apply(comparison);
      if (value == null) {
        continue;
      }
      if (best == null
          || compare(value, bestValue, higherIsBetter) < 0
          || (compare(value, bestValue, higherIsBetter) == 0 && tieBreak(comparison, best) < 0)) {
        best = comparison;
        bestValue = value;
      }
    }
    return best == null ? "n/a" : best.configuration();
  }

  private static int compare(Double left, Double right, boolean higherIsBetter) {
    int comparison = Double.compare(left, right);
    return higherIsBetter ? -comparison : comparison;
  }

  private static int tieBreak(ThresholdComparison left, ThresholdComparison right) {
    if (left.baseline() != right.baseline()) {
      return left.baseline() ? -1 : 1;
    }
    return left.configuration().compareTo(right.configuration());
  }
}
