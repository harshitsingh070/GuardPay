package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Confirmation returned to the reporter; never includes other users' data. */
public record FraudReportResponse(
    UUID reportId,
    UUID recipientReference,
    String status,
    OffsetDateTime submittedAt) {}
