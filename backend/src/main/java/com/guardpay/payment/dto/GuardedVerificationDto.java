package com.guardpay.payment.dto;

import com.guardpay.payment.VerificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Controlled verification challenge returned with a VERIFY decision.
 *
 * <p>The answer itself is never part of this DTO. It is delivered through a
 * separate out-of-band channel (in the demo: a local-only server outbox).
 */
public record GuardedVerificationDto(
    UUID verificationId,
    VerificationStatus status,
    OffsetDateTime expiresAt,
    int attemptsRemaining,
    boolean simulated,
    String channelHint) {}
