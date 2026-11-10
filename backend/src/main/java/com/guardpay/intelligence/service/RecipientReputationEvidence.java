package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.MatchStatus;
import com.guardpay.intelligence.domain.ReputationCategory;
import com.guardpay.intelligence.domain.ReputationStatus;

/**
 * Evidence-only view of a recipient's global reputation.
 *
 * <p>This record never carries raw identifiers and never decides the payment.
 * The deterministic engine may add {@code riskPoints}; a lookup failure is
 * {@code UNAVAILABLE}, not a clean result.
 */
public record RecipientReputationEvidence(
    MatchStatus status,
    ReputationStatus reputationStatus,
    ReputationCategory category,
    int reputationScore,
    int riskPoints,
    IdentifierMatchLevel matchLevel,
    IdentityConfidence identityConfidence,
    int evidenceCount,
    int reportCount,
    String reason) {

  public RecipientReputationEvidence {
    status = status == null ? MatchStatus.UNAVAILABLE : status;
    reputationStatus = reputationStatus == null ? ReputationStatus.UNKNOWN : reputationStatus;
    category = category == null ? ReputationCategory.NO_DATA : category;
    matchLevel = matchLevel == null ? IdentifierMatchLevel.NO_MATCH : matchLevel;
    identityConfidence = identityConfidence == null ? IdentityConfidence.LOW : identityConfidence;
    riskPoints = Math.max(0, Math.min(riskPoints, RecipientReputationPoints.MAX_POINTS));
  }

  public static RecipientReputationEvidence unavailable() {
    return new RecipientReputationEvidence(
        MatchStatus.UNAVAILABLE,
        ReputationStatus.UNKNOWN,
        ReputationCategory.NO_DATA,
        0,
        0,
        IdentifierMatchLevel.NO_MATCH,
        IdentityConfidence.LOW,
        0,
        0,
        "RECIPIENT_INTELLIGENCE_UNAVAILABLE");
  }

  public static RecipientReputationEvidence noData() {
    return new RecipientReputationEvidence(
        MatchStatus.NO_DATA,
        ReputationStatus.UNKNOWN,
        ReputationCategory.NO_DATA,
        0,
        0,
        IdentifierMatchLevel.NO_MATCH,
        IdentityConfidence.LOW,
        0,
        0,
        "NO_GLOBAL_RECIPIENT_DATA");
  }

  public static RecipientReputationEvidence noMatch() {
    return new RecipientReputationEvidence(
        MatchStatus.NO_MATCH,
        ReputationStatus.UNKNOWN,
        ReputationCategory.NO_DATA,
        0,
        0,
        IdentifierMatchLevel.NO_MATCH,
        IdentityConfidence.LOW,
        0,
        0,
        "NO_IDENTIFIER_MATCH");
  }

  public boolean contributes() {
    return status == MatchStatus.MATCH_FOUND && riskPoints > 0;
  }
}
