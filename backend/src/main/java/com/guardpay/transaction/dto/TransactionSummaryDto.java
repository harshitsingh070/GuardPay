package com.guardpay.transaction.dto;

import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row of the transaction history table. */
public record TransactionSummaryDto(
    UUID id,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    OffsetDateTime transactionTimestamp,
    String description,
    String recipientName,
    String merchantName,
    Integer riskScore,
    RiskLevel riskLevel,
    RecommendedAction recommendedAction) {}
