package com.guardpay.intelligence.dto;

import com.guardpay.intelligence.domain.IdentifierType;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Report submission body.
 *
 * <p>Either a {@code relatedTransactionId} the caller owns, or an explicit
 * identifier is required. {@code recipientReference} alone is never accepted:
 * it cannot prove the caller has a relationship with the recipient.
 */
public record SubmitFraudReportRequest(
    UUID relatedTransactionId,
    UUID recipientReference,
    IdentifierType identifierType,
    String identifierValue,
    String description,
    OffsetDateTime incidentAt) {}
