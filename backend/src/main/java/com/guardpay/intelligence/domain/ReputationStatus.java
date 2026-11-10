package com.guardpay.intelligence.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Global recipient reputation lifecycle.
 *
 * <p>{@code CONFIRMED_FRAUD} is reachable only through an explicit admin
 * review. Automatic evidence may corroborate suspicion but can never confirm
 * fraud. Transitions are declared centrally so no service can skip the
 * review gate.
 */
public enum ReputationStatus {
  UNKNOWN,
  REPORTED,
  UNDER_REVIEW,
  CORROBORATED_SUSPICIOUS,
  CONFIRMED_FRAUD,
  CLEARED,
  EXPIRED;

  private static final Map<ReputationStatus, Set<ReputationStatus>> TRANSITIONS =
      Map.of(
          UNKNOWN, EnumSet.of(REPORTED, UNDER_REVIEW, CLEARED),
          REPORTED, EnumSet.of(UNDER_REVIEW, CLEARED, EXPIRED, UNKNOWN),
          UNDER_REVIEW,
              EnumSet.of(
                  CORROBORATED_SUSPICIOUS, CONFIRMED_FRAUD, CLEARED, REPORTED, EXPIRED, UNKNOWN),
          CORROBORATED_SUSPICIOUS,
              EnumSet.of(UNDER_REVIEW, CONFIRMED_FRAUD, CLEARED, EXPIRED, UNKNOWN),
          CONFIRMED_FRAUD, EnumSet.noneOf(ReputationStatus.class),
          CLEARED, EnumSet.of(REPORTED, UNDER_REVIEW),
          EXPIRED, EnumSet.of(REPORTED, UNDER_REVIEW, UNKNOWN));

  public boolean canTransitionTo(ReputationStatus target) {
    return target != null && TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }

  public boolean isTerminal() {
    return this == CONFIRMED_FRAUD;
  }
}
