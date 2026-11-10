package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Admin-only report row; reporter reference is not exposed. */
public record AdminReportDto(
    UUID reportId,
    String status,
    String description,
    OffsetDateTime submittedAt,
    OffsetDateTime incidentAt,
    UUID relatedTransactionId) {}
