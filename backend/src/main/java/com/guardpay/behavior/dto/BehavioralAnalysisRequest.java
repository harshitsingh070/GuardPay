package com.guardpay.behavior.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request for behavioral analysis of an incoming payment against the
 * user's learned profile.
 */
public record BehavioralAnalysisRequest(
    UUID userId,
    BigDecimal amount,
    String currency,
    OffsetDateTime transactionTimestamp,
    UUID recipientId,
    String recipientName,
    UUID merchantId,
    UUID deviceId,
    String deviceFingerprint,
    String geoCountry,
    String geoCity,
    Integer asn,
    Boolean vpnOrProxy,
    UUID transactionId,
    OffsetDateTime asOf) {}
