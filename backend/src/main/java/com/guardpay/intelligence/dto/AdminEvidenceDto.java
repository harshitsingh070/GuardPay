package com.guardpay.intelligence.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Admin-only evidence row; reporter identities stay masked. */
public record AdminEvidenceDto(
    String evidenceType,
    String source,
    String verificationStatus,
    BigDecimal confidence,
    int reportCount,
    OffsetDateTime observedAt,
    OffsetDateTime lastObservedAt,
    boolean revoked,
    String details) {}
