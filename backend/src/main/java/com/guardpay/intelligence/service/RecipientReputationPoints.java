package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.ReputationStatus;

/**
 * Maps global reputation into bounded risk points.
 *
 * <p>Status alone is not enough: points are scaled by how strongly the payment
 * identifier matches the entity and by identity confidence. The cap keeps a
 * single external reputation fact from dominating the score, and the engine
 * still applies its own hard-block rule.
 */
public final class RecipientReputationPoints {

  public static final int MAX_POINTS = 40;

  private RecipientReputationPoints() {}

  public static int basePoints(ReputationStatus status) {
    if (status == null) {
      return 0;
    }
    return switch (status) {
      case CONFIRMED_FRAUD -> 40;
      case CORROBORATED_SUSPICIOUS -> 25;
      case UNDER_REVIEW -> 15;
      case REPORTED -> 10;
      default -> 0;
    };
  }

  public static int points(
      ReputationStatus status,
      IdentifierMatchLevel matchLevel,
      IdentityConfidence confidence) {
    int base = basePoints(status);
    if (base == 0) {
      return 0;
    }
    double match = matchLevel == null ? 0.0 : matchLevel.multiplier();
    double identity = 0.5 + 0.5 * (confidence == null ? 0.0 : confidence.factor());
    int scaled = (int) Math.round(base * match * identity);
    return Math.max(0, Math.min(scaled, MAX_POINTS));
  }
}
