package com.guardpay.risk.signal;

/**
 * Backend-resolved device call state for one evaluation. {@code null}
 * {@code activeCall} means unavailable — the reason code says why
 * ({@code NOT_AVAILABLE} for missing/unknown device, {@code STALE} for an
 * expired observation). Built server-side only, never deserialized.
 */
public record DeviceCallSignal(Boolean activeCall, String unavailabilityReason) {}
