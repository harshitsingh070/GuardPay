package com.guardpay.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guardpay.payment.VerificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controlled verification challenge returned with a VERIFY decision.
 *
 * <p>{@code challengeCode} is present only for the clearly-labelled simulated
 * channel used when no real identity provider is configured; a production
 * channel would deliver the answer out of band and never echo it here.
 */
public record GuardedVerificationDto(
    UUID verificationId,
    VerificationStatus status,
    OffsetDateTime expiresAt,
    int attemptsRemaining,
    boolean simulated,
    String channelHint,
    @JsonInclude(JsonInclude.Include.NON_NULL) String challengeCode) {}
