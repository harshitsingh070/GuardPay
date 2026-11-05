package com.guardpay.risk.blocklist.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One blocklisted counterparty, without exposing any payment credentials. */
public record BlocklistEntryDto(
    UUID id,
    UUID recipientId,
    String recipientName,
    String recipientEmail,
    String reason,
    OffsetDateTime createdAt) {}
