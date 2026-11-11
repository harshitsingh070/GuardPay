package com.guardpay.feedback;

import java.time.OffsetDateTime;

/** Audit event for a feedback label. */
public record FeedbackEventDto(
    String eventType,
    String fromStatus,
    String toStatus,
    String detail,
    OffsetDateTime occurredAt) {}
