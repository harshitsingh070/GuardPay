package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Authorized recipient summary; evidence detail is intentionally omitted. */
public record RecipientReputationSummaryDto(
    UUID recipientReference,
    String matchStatus,
    String reputationStatus,
    String category,
    int score,
    int evidenceCount,
    int reportCount,
    String matchLevel,
    String identityConfidence,
    OffsetDateTime lastEvidenceAt,
    String disclaimer) {}
