package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One report owned by the requesting user. */
public record MyReportDto(
    UUID reportId,
    UUID recipientReference,
    String status,
    OffsetDateTime submittedAt,
    OffsetDateTime incidentAt,
    OffsetDateTime reviewedAt,
    String reviewNote) {}
