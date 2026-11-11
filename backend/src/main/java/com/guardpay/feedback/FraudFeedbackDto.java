package com.guardpay.feedback;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Stored feedback with the decision snapshot used for evaluation. */
public record FraudFeedbackDto(
    UUID id,
    UUID transactionId,
    String label,
    String feedbackType,
    String feedbackStatus,
    String source,
    String decisionAction,
    Integer riskScore,
    UUID riskAssessmentId,
    UUID recipientEntityId,
    String relatedFindingCode,
    String reasonCode,
    String reviewerReference,
    Double reviewerConfidence,
    OffsetDateTime reviewedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
