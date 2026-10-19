package com.guardpay.risk;

/**
 * One evaluated signal. All known signals are always listed (in
 * {@link RiskSignal} order); {@code triggered} marks the ones that fired.
 * Individual findings are never persisted — only the final assessment is.
 */
public record RiskFinding(String code, String title, String description, int points, boolean triggered) {}
