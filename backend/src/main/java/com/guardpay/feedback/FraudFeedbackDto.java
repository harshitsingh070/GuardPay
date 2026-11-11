package com.guardpay.feedback;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Stored feedback with the decision snapshot used for evaluation. */
public record FraudFeedbackDto(
    UUID id,
    UUID transactionId,
    String label,
    String source,
    String decisionAction,
    Integer riskScore,
    OffsetDateTime createdAt) {}
