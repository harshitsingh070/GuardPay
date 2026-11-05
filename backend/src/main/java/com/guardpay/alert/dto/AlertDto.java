package com.guardpay.alert.dto;

import com.guardpay.alert.AlertSeverity;
import com.guardpay.alert.AlertType;
import com.guardpay.risk.RecommendedAction;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One alert as shown in the in-app feed and the SSE stream. */
public record AlertDto(
    UUID id,
    UUID transactionId,
    AlertType type,
    AlertSeverity severity,
    String title,
    String message,
    Integer score,
    RecommendedAction recommendedAction,
    BigDecimal amount,
    String currency,
    String recipientName,
    List<String> signals,
    OffsetDateTime createdAt) {}
