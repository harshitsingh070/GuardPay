package com.guardpay.transaction.dto;

import com.guardpay.payment.VerificationStatus;
import com.guardpay.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full transaction detail: what was paid, the PayPal references, the latest
 * risk decision (with findings) and the controlled-verification outcome.
 */
public record TransactionDetailDto(
    UUID id,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String description,
    OffsetDateTime transactionTimestamp,
    OffsetDateTime createdAt,
    String recipientName,
    String merchantName,
    String paypalOrderId,
    String paypalCaptureId,
    String paypalTransactionId,
    RiskAssessmentViewDto assessment,
    VerificationStatus verificationStatus) {}
