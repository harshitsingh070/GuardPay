package com.guardpay.intelligence.dto;

import com.guardpay.intelligence.service.RecipientReputationEvidence;

/**
 * Safe, identifier-free reputation block shared by evaluation, transaction
 * detail, and intelligence endpoints.
 */
public record RecipientReputationDto(
    String status,
    String reputationStatus,
    String category,
    int score,
    int points,
    String matchLevel,
    String identityConfidence,
    int evidenceCount,
    int reportCount,
    String reason) {

  public static RecipientReputationDto from(RecipientReputationEvidence evidence) {
    if (evidence == null) {
      return null;
    }
    return new RecipientReputationDto(
        evidence.status().name(),
        evidence.reputationStatus().name(),
        evidence.category().name(),
        evidence.reputationScore(),
        evidence.riskPoints(),
        evidence.matchLevel().name(),
        evidence.identityConfidence().name(),
        evidence.evidenceCount(),
        evidence.reportCount(),
        evidence.reason());
  }
}
