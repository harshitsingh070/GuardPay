package com.guardpay.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Data-driven observations derived only from the current benchmark run. */
final class BenchmarkRecommendations {

  private BenchmarkRecommendations() {}

  static List<String> generate(
      BenchmarkMode mode,
      BenchmarkMetrics overall,
      Map<String, CategoryMetrics> categories,
      ThresholdAnalysis thresholds) {
    List<String> recommendations = new ArrayList<>();
    Map<String, CategoryMetrics> sortedCategories = new TreeMap<>(categories);

    CategoryMetrics worstFalseNegative =
        sortedCategories.values().stream()
            .filter(category -> category.fraudScenarios() > 0 && category.falseNegatives() > 0)
            .max(
                Comparator.comparingDouble(
                        (CategoryMetrics category) ->
                            (double) category.falseNegatives() / category.fraudScenarios())
                    .thenComparingInt(CategoryMetrics::falseNegatives)
                    .thenComparing(CategoryMetrics::category, Comparator.reverseOrder()))
            .orElse(null);
    if (worstFalseNegative == null) {
      recommendations.add(
          "No primary category had a fraud false negative in " + mode.name() + " mode.");
    } else {
      recommendations.add(
          "Highest fraud false-negative rate: "
              + worstFalseNegative.category()
              + " at "
              + percent(
                  (double) worstFalseNegative.falseNegatives()
                      / worstFalseNegative.fraudScenarios())
              + " ("
              + worstFalseNegative.falseNegatives()
              + "/"
              + worstFalseNegative.fraudScenarios()
              + " fraud scenarios allowed) in "
              + mode.name()
              + " mode.");
    }

    CategoryMetrics worstFalsePositive =
        sortedCategories.values().stream()
            .filter(
                category ->
                    category.legitimateScenarios() > 0 && category.falsePositives() > 0)
            .max(
                Comparator.comparingDouble(
                        (CategoryMetrics category) ->
                            (double) category.falsePositives() / category.legitimateScenarios())
                    .thenComparingInt(CategoryMetrics::falsePositives)
                    .thenComparing(CategoryMetrics::category, Comparator.reverseOrder()))
            .orElse(null);
    if (worstFalsePositive == null) {
      recommendations.add(
          "No primary category had a legitimate false positive in " + mode.name() + " mode.");
    } else {
      recommendations.add(
          "Highest legitimate false-positive rate: "
              + worstFalsePositive.category()
              + " at "
              + percent(
                  (double) worstFalsePositive.falsePositives()
                      / worstFalsePositive.legitimateScenarios())
              + " ("
              + worstFalsePositive.falsePositives()
              + "/"
              + worstFalsePositive.legitimateScenarios()
              + " legitimate scenarios sent to VERIFY or STOP; "
              + worstFalsePositive.legitimateStopped()
              + " STOP) in "
              + mode.name()
              + " mode.");
    }

    addThresholdRecommendation(
        recommendations,
        thresholds,
        "recall",
        "Highest recall",
        mode);
    addThresholdRecommendation(
        recommendations,
        thresholds,
        "f1",
        "Highest F1",
        mode);
    addThresholdRecommendation(
        recommendations,
        thresholds,
        "legitimatePaymentFriction",
        "Lowest legitimate payment friction",
        mode);

    ThresholdComparison baseline =
        thresholds.comparisons().stream()
            .filter(ThresholdComparison::baseline)
            .findFirst()
            .orElse(null);
    ThresholdComparison bestF1 =
        findComparison(thresholds, thresholds.best().get("f1")).orElse(null);
    ThresholdComparison bestRecall =
        findComparison(thresholds, thresholds.best().get("recall")).orElse(null);
    if (baseline != null) {
      recommendations.add(
          "Production baseline in "
              + mode.name()
              + " mode: recall "
              + percent(overall.recall())
              + ", F1 "
              + percent(overall.f1())
              + ", legitimate friction "
              + overall.legitimateVerified()
              + " VERIFY + "
              + overall.legitimateStopped()
              + " STOP.");
      if (bestF1 != null && bestRecall != null) {
        if (bestF1.configuration().equals(bestRecall.configuration())) {
          recommendations.add(
              "Best F1 and recall coincide in "
                  + mode.name()
                  + " mode at "
                  + bestF1.configuration()
                  + " (F1 "
                  + percent(bestF1.f1())
                  + ", recall "
                  + percent(bestRecall.recall())
                  + "); compare its precision and legitimate friction with the baseline before "
                  + "considering any production change.");
        } else {
          recommendations.add(
              "Trade-off observed in "
                  + mode.name()
                  + " mode: best F1 is "
                  + bestF1.configuration()
                  + " ("
                  + percent(bestF1.f1())
                  + "), while best recall is "
                  + bestRecall.configuration()
                  + " ("
                  + percent(bestRecall.recall())
                  + "); these are benchmark-specific trade-offs, not a universal optimum.");
        }
      }
    }
    return List.copyOf(recommendations);
  }

  private static void addThresholdRecommendation(
      List<String> recommendations,
      ThresholdAnalysis thresholds,
      String metric,
      String label,
      BenchmarkMode mode) {
    String configuration = thresholds.best().get(metric);
    if (configuration == null || "n/a".equals(configuration)) {
      recommendations.add(label + " could not be ranked for " + mode.name() + " mode.");
      return;
    }
    ThresholdComparison comparison = findComparison(thresholds, configuration).orElse(null);
    if (comparison == null) {
      recommendations.add(label + " could not be ranked for " + mode.name() + " mode.");
      return;
    }
    switch (metric) {
      case "recall" ->
          recommendations.add(
              "Highest recall: "
                  + configuration
                  + " at "
                  + percent(comparison.recall())
                  + " ("
                  + comparison.truePositives()
                  + "/"
                  + (comparison.truePositives() + comparison.falseNegatives())
                  + " fraud scenarios detected), with "
                  + comparison.legitimateVerified()
                  + " legitimate VERIFY and "
                  + comparison.legitimateStopped()
                  + " legitimate STOP in "
                  + mode.name()
                  + " mode.");
      case "f1" ->
          recommendations.add(
              "Highest F1: "
                  + configuration
                  + " at "
                  + percent(comparison.f1())
                  + " with precision "
                  + percent(comparison.precision())
                  + " and recall "
                  + percent(comparison.recall())
                  + " in "
                  + mode.name()
                  + " mode.");
      case "legitimatePaymentFriction" ->
          recommendations.add(
              "Lowest legitimate payment friction: "
                  + configuration
                  + " with "
                  + comparison.legitimateVerified()
                  + " legitimate VERIFY and "
                  + comparison.legitimateStopped()
                  + " legitimate STOP ("
                  + comparison.legitimatePaymentFriction()
                  + " false positives) in "
                  + mode.name()
                  + " mode.");
      default -> recommendations.add(label + " is " + configuration + " in " + mode.name() + " mode.");
    }
  }

  private static Optional<ThresholdComparison> findComparison(
      ThresholdAnalysis thresholds, String configuration) {
    if (configuration == null) {
      return Optional.empty();
    }
    return thresholds.comparisons().stream()
        .filter(comparison -> comparison.configuration().equals(configuration))
        .findFirst();
  }

  private static String percent(Double value) {
    return value == null ? "n/a" : String.format(Locale.ROOT, "%.2f%%", value * 100.0);
  }
}
