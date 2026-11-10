package com.guardpay.risk.correlation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns fired rules into a bounded, explainable contribution.
 *
 * <p>Formula:
 *
 * <pre>
 *   strength(s)   = 0.5 * severity + 0.3 * confidence + 0.2 * normalizedPoints
 *   Q(rule)       = geometricMean(strengths) * independenceFactor
 *   C(rule)       = noisyOR(max confidence per evidence family)
 *   I(rule)       = weight * Q * C * freshness
 *   S             = 1 - product(1 - I(rule))
 *   points        = round(S * maxContributionPoints)
 * </pre>
 *
 * <p>Points are allocated proportionally to interaction strength, then capped
 * per evidence family so a single family cannot dominate. The integer
 * allocation uses largest-remainder rounding, so the sum always equals the
 * reported contribution.
 */
public final class CorrelationScoreCalculator {

  private final CorrelationOverlapResolver overlapResolver;

  public CorrelationScoreCalculator() {
    this(new CorrelationOverlapResolver());
  }

  public CorrelationScoreCalculator(CorrelationOverlapResolver overlapResolver) {
    this.overlapResolver = overlapResolver;
  }

  CorrelationResult calculate(
      List<RuleMatch> matches, CorrelationConfiguration configuration) {
    if (matches.isEmpty()) {
      return CorrelationResult.empty(CorrelationResult.NO_MATCH);
    }

    List<ResolvedRule> resolved = overlapResolver.resolve(matches, configuration);
    double aggregate = aggregateStrength(resolved);

    int target = (int) Math.round(aggregate * configuration.maxContributionPoints());
    target = Math.max(0, Math.min(target, configuration.maxContributionPoints()));

    double totalInteraction =
        resolved.stream().mapToDouble(ResolvedRule::interaction).sum();
    double[] allocations = new double[resolved.size()];
    if (totalInteraction > 0.0) {
      for (int index = 0; index < resolved.size(); index++) {
        allocations[index] =
            target * resolved.get(index).interaction() / totalInteraction;
      }
    }

    applyFamilyCap(allocations, resolved, configuration.familyContributionCap());
    int[] points = largestRemainder(allocations, target);

    List<CorrelationFinding> findings = new ArrayList<>(resolved.size());
    for (int index = 0; index < resolved.size(); index++) {
      findings.add(toFinding(resolved.get(index), points[index]));
    }
    int contribution = Arrays.stream(points).sum();

    return new CorrelationResult(
        List.copyOf(findings),
        contribution,
        aggregate,
        CorrelationResult.AVAILABLE,
        explain(findings, contribution));
  }

  private static double aggregateStrength(List<ResolvedRule> resolved) {
    double remaining = 1.0;
    for (ResolvedRule rule : resolved) {
      remaining *= 1.0 - Math.min(0.999, rule.interaction());
    }
    return clamp01(1.0 - remaining);
  }

  private static void applyFamilyCap(
      double[] allocations, List<ResolvedRule> resolved, int cap) {
    if (cap <= 0) {
      return;
    }
    for (int iteration = 0; iteration < 4; iteration++) {
      Map<EvidenceFamily, Double> usage = new EnumMap<>(EvidenceFamily.class);
      for (int index = 0; index < resolved.size(); index++) {
        Set<EvidenceFamily> families = resolved.get(index).match().rule().families();
        if (families.isEmpty()) {
          continue;
        }
        double share = allocations[index] / families.size();
        for (EvidenceFamily family : families) {
          usage.merge(family, share, Double::sum);
        }
      }

      boolean changed = false;
      for (int index = 0; index < resolved.size(); index++) {
        double scale = 1.0;
        for (EvidenceFamily family : resolved.get(index).match().rule().families()) {
          double used = usage.getOrDefault(family, 0.0);
          if (used > cap) {
            scale = Math.min(scale, cap / used);
          }
        }
        if (scale < 1.0) {
          allocations[index] *= scale;
          changed = true;
        }
      }
      if (!changed) {
        return;
      }
    }
  }

  private static int[] largestRemainder(double[] values, int target) {
    int[] points = new int[values.length];
    if (values.length == 0 || target <= 0) {
      return points;
    }
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    int effective = Math.max(0, Math.min(target, (int) Math.round(sum)));

    double[] remainders = new double[values.length];
    int assigned = 0;
    for (int index = 0; index < values.length; index++) {
      points[index] = (int) Math.floor(values[index]);
      remainders[index] = values[index] - points[index];
      assigned += points[index];
    }
    int leftover = effective - assigned;
    Integer[] order = new Integer[values.length];
    for (int index = 0; index < order.length; index++) {
      order[index] = index;
    }
    Arrays.sort(
        order,
        Comparator.comparingDouble((Integer index) -> remainders[index])
            .reversed()
            .thenComparingInt(Integer::intValue));
    for (int step = 0; step < leftover; step++) {
      points[order[step % order.length]]++;
    }
    return points;
  }

  private static CorrelationFinding toFinding(ResolvedRule resolved, int points) {
    CorrelationRule rule = resolved.match().rule();
    List<CorrelationSignal> participants = resolved.participants();
    List<String> codes = participants.stream().map(CorrelationSignal::code).toList();
    List<String> families =
        rule.families().stream().map(EvidenceFamily::name).toList();
    String severity = highestSeverity(participants);
    double confidence = averageConfidence(participants);
    String explanation =
        rule.description()
            + " Contributing signals: "
            + String.join(", ", codes)
            + ".";
    if (resolved.overlapDiscounted()) {
      explanation += " Overlap with a stronger correlation was discounted.";
    }
    return new CorrelationFinding(
        rule.code(),
        rule.category(),
        rule.title(),
        rule.description(),
        severity,
        confidence,
        resolved.interaction(),
        points,
        codes,
        families,
        codes,
        resolved.match().unavailableSignals(),
        rule.ruleVersion(),
        resolved.overlapDiscounted(),
        explanation);
  }

  private static String highestSeverity(List<CorrelationSignal> signals) {
    String highest = "LOW";
    int highestRank = 0;
    for (CorrelationSignal signal : signals) {
      int rank = severityRank(signal.severity());
      if (rank > highestRank) {
        highestRank = rank;
        highest = signal.severity().toUpperCase(Locale.ROOT);
      }
    }
    return highest;
  }

  private static int severityRank(String severity) {
    if (severity == null) {
      return 0;
    }
    return switch (severity.toUpperCase(Locale.ROOT)) {
      case "CRITICAL" -> 4;
      case "HIGH" -> 3;
      case "MEDIUM" -> 2;
      case "LOW" -> 1;
      default -> 0;
    };
  }

  private static double averageConfidence(List<CorrelationSignal> signals) {
    return signals.stream().mapToDouble(CorrelationSignal::confidence).average().orElse(0.0);
  }

  private static String explain(List<CorrelationFinding> findings, int contribution) {
    if (findings.isEmpty()) {
      return "No correlated signal combination was detected.";
    }
    String titles =
        findings.stream().map(CorrelationFinding::title).collect(Collectors.joining("; "));
    return "GuardPay detected "
        + findings.size()
        + (findings.size() == 1 ? " correlated signal combination" : " correlated signal combinations")
        + " ("
        + titles
        + "), adding "
        + contribution
        + (contribution == 1 ? " risk point." : " risk points.");
  }

  private static double clamp01(double value) {
    if (value < 0.0) {
      return 0.0;
    }
    return Math.min(value, 1.0);
  }
}
