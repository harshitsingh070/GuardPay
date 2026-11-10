package com.guardpay.risk.correlation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prevents overlapping rules from being counted as independent evidence.
 *
 * <p>Rules are processed strongest-first. A later rule that shares at least
 * {@code overlapThreshold} of its evidence families with a stronger rule is
 * discounted by {@code dependencyDiscount}; a rule with exactly the same
 * participating signals is dropped entirely. This is what stops
 * {@code NEW_DEVICE + DEVICE_BEHAVIOR_DEVIATION + OVERALL_BEHAVIOR_DEVIATION}
 * from being counted three times.
 */
final class CorrelationOverlapResolver {

  List<ResolvedRule> resolve(List<RuleMatch> matches, CorrelationConfiguration configuration) {
    List<RuleMatch> ordered = new ArrayList<>(matches);
    ordered.sort(
        Comparator.comparingDouble(RuleMatch::interaction)
            .reversed()
            .thenComparing(match -> match.rule().code()));

    List<ResolvedRule> resolved = new ArrayList<>();
    for (RuleMatch match : ordered) {
      Set<String> codes = codesOf(match);
      boolean duplicate =
          resolved.stream().anyMatch(kept -> codesOf(kept.match()).equals(codes));
      if (duplicate) {
        continue;
      }
      double factor = 1.0;
      for (ResolvedRule kept : resolved) {
        double ratio = overlapRatio(match.rule().families(), kept.match().rule().families());
        if (ratio >= configuration.overlapThreshold()) {
          factor = Math.min(factor, configuration.dependencyDiscount());
        }
      }
      resolved.add(new ResolvedRule(match, match.interaction() * factor, factor, factor < 1.0));
    }
    return List.copyOf(resolved);
  }

  private static Set<String> codesOf(RuleMatch match) {
    Set<String> codes = new HashSet<>();
    for (CorrelationSignal signal : match.participants()) {
      codes.add(signal.code());
    }
    return codes;
  }

  private static double overlapRatio(
      Set<EvidenceFamily> left, Set<EvidenceFamily> right) {
    if (left.isEmpty() || right.isEmpty()) {
      return 0.0;
    }
    Set<EvidenceFamily> intersection = new HashSet<>(left);
    intersection.retainAll(right);
    Set<EvidenceFamily> union = new HashSet<>(left);
    union.addAll(right);
    return (double) intersection.size() / union.size();
  }
}
