package com.guardpay.behavior;

/**
 * Result of cold-start profile assessment. Determines which behavioral
 * dimensions can be reliably evaluated.
 */
public record ProfileConfidence(
    UserBehaviorProfile.ProfileStatus status,
    Integer transactionCount,
    Boolean sufficientForAmount,
    Boolean sufficientForTime,
    Boolean sufficientForRecipient,
    Boolean sufficientForDevice,
    Boolean sufficientForGeo,
    Boolean sufficientForFrequency,
    String reason) {}
