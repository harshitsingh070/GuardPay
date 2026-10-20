package com.guardpay.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.payment.VerificationStatus;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of confirming a VERIFY challenge. The server decides the outcome and,
 * only on success, creates the PayPal order to continue with.
 */
public record VerificationConfirmResponseDto(
    UUID paymentId,
    VerificationStatus verificationStatus,
    GuardedPaymentStatus paymentStatus,
    @JsonInclude(JsonInclude.Include.NON_NULL) GuardedPaypalOrderDto paypalOrder,
    OffsetDateTime expiresAt,
    int attemptsRemaining,
    String message) {}
