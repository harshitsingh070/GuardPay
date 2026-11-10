package com.guardpay.risk.correlation;

import com.guardpay.risk.signal.PaymentSignal;
import com.guardpay.risk.signal.SignalSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Deterministic signal-correlation engine.
 *
 * <p>It consumes the same {@link PaymentSignal} collection the deterministic
 * engine already uses and reports suspicious <em>combinations</em>. It never
 * approves or rejects a payment itself: its bounded contribution is added to
 * the engine score, and the engine remains the only decision authority.
 *
 * <p>The whole pass is fail-safe: any unexpected error returns an
 * evidence-neutral {@link CorrelationResult#unavailable()} so a correlation
 * defect can never block or approve a payment.
 */
@Service
public class RiskCorrelationEngine {

  private static final Logger log = LoggerFactory.getLogger(RiskCorrelationEngine.class);

  private final CorrelationSignalNormalizer normalizer;
  private final CorrelationRuleRegistry registry;
  private final CorrelationScoreCalculator calculator;
  private final CorrelationConfiguration configuration;

  public RiskCorrelationEngine() {
    this(
        new CorrelationSignalNormalizer(),
        CorrelationRuleRegistry.defaults(),
        new CorrelationScoreCalculator(),
        CorrelationConfiguration.defaults());
  }

  public RiskCorrelationEngine(
      CorrelationSignalNormalizer normalizer,
      CorrelationRuleRegistry registry,
      CorrelationScoreCalculator calculator,
      CorrelationConfiguration configuration) {
    this.normalizer = normalizer;
    this.registry = registry;
    this.calculator = calculator;
    this.configuration = configuration;
  }

  public CorrelationResult evaluate(List<PaymentSignal> signals) {
    try {
      List<CorrelationSignal> normalized = normalizer.normalize(signals);
      boolean anyPresent = normalized.stream().anyMatch(CorrelationSignal::present);
      if (!anyPresent) {
        return CorrelationResult.empty(
            normalized.isEmpty()
                ? CorrelationResult.NO_SIGNALS
                : CorrelationResult.NO_MATCH);
      }
      List<RuleMatch> matches = match(normalized);
      if (matches.isEmpty()) {
        return CorrelationResult.empty(CorrelationResult.NO_MATCH);
      }
      return calculator.calculate(matches, configuration);
    } catch (RuntimeException failure) {
      log.warn("Signal correlation failed; continuing with deterministic evidence only", failure);
      return CorrelationResult.unavailable();
    }
  }

  private List<RuleMatch> match(List<CorrelationSignal> signals) {
    Map<String, CorrelationSignal> byCode = new LinkedHashMap<>();
    for (CorrelationSignal signal : signals) {
      byCode.merge(signal.code(), signal, RiskCorrelationEngine::prefer);
    }

    List<RuleMatch> matches = new ArrayList<>();
    for (CorrelationRule rule : registry.enabledRules()) {
      List<CorrelationSignal> participants = new ArrayList<>();
      List<String> unavailable = new ArrayList<>();
      boolean satisfied = true;

      for (SignalRequirement requirement : rule.requirements()) {
        CorrelationSignal best = null;
        for (String code : requirement.codes()) {
          CorrelationSignal candidate = byCode.get(code);
          if (candidate == null || !candidate.present()) {
            continue;
          }
          if (lowConfidence(candidate, rule)) {
            continue;
          }
          if (best == null
              || candidate.confidence() > best.confidence()
              || (candidate.confidence() == best.confidence()
                  && candidate.originalPoints() > best.originalPoints())) {
            best = candidate;
          }
        }
        if (best == null) {
          satisfied = false;
          continue;
        }
        participants.add(best);
        for (String code : requirement.codes()) {
          CorrelationSignal alternative = byCode.get(code);
          if (alternative != null
              && alternative != best
              && (alternative.availability() == SignalAvailability.UNAVAILABLE
                  || alternative.availability() == SignalAvailability.UNKNOWN)) {
            unavailable.add(code);
          }
        }
      }

      if (!satisfied) {
        continue;
      }
      double interaction = interaction(rule, participants);
      double threshold =
          Math.max(rule.minimumInteraction(), configuration.interactionThreshold());
      if (interaction < threshold) {
        continue;
      }
      matches.add(new RuleMatch(rule, participants, interaction, unavailable));
    }
    return matches;
  }

  private boolean lowConfidence(CorrelationSignal signal, CorrelationRule rule) {
    if (signal.source() != SignalSource.AI
        && signal.source() != SignalSource.BEHAVIORAL_PROFILE) {
      return false;
    }
    return signal.confidence()
        < Math.max(rule.minimumConfidence(), configuration.minimumSignalConfidence());
  }

  private double interaction(CorrelationRule rule, List<CorrelationSignal> participants) {
    double[] strengths = participants.stream().mapToDouble(this::strength).toArray();
    double quality = geometricMean(strengths);

    long distinctFamilies =
        participants.stream().map(CorrelationSignal::family).distinct().count();
    double independence = 0.5 + 0.5 * ((double) distinctFamilies / participants.size());
    quality *= independence;

    double combination = noisyOr(participants);
    return clamp01(rule.weight() * quality * combination * configuration.freshnessFactor());
  }

  private double strength(CorrelationSignal signal) {
    double severity = severityWeight(signal.severity());
    double confidence = clamp01(signal.confidence());
    double points = clamp01(signal.originalPoints() / 25.0);
    return clamp01(0.5 * severity + 0.3 * confidence + 0.2 * points);
  }

  private double geometricMean(double[] values) {
    if (values.length == 0) {
      return 0.0;
    }
    double total = 0.0;
    for (double value : values) {
      total += Math.log(Math.max(value, configuration.strengthFloor()));
    }
    return clamp01(Math.exp(total / values.length));
  }

  private static double noisyOr(List<CorrelationSignal> participants) {
    Map<EvidenceFamily, Double> maxByFamily = new EnumMap<>(EvidenceFamily.class);
    for (CorrelationSignal signal : participants) {
      maxByFamily.merge(signal.family(), clamp01(signal.confidence()), Math::max);
    }
    double remaining = 1.0;
    for (double confidence : maxByFamily.values()) {
      remaining *= 1.0 - confidence;
    }
    return clamp01(1.0 - remaining);
  }

  private static double severityWeight(String severity) {
    if (severity == null) {
      return 0.6;
    }
    return switch (severity.toUpperCase(java.util.Locale.ROOT)) {
      case "CRITICAL", "HIGH" -> 1.0;
      case "MEDIUM" -> 0.75;
      case "LOW" -> 0.5;
      default -> 0.6;
    };
  }

  private static CorrelationSignal prefer(CorrelationSignal left, CorrelationSignal right) {
    if (left.present() != right.present()) {
      return left.present() ? left : right;
    }
    return left.confidence() >= right.confidence() ? left : right;
  }

  private static double clamp01(double value) {
    if (value < 0.0) {
      return 0.0;
    }
    return Math.min(value, 1.0);
  }
}
