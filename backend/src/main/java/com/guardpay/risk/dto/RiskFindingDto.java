package com.guardpay.risk.dto;

/**
 * One evaluated signal, mirroring the engine finding. Persisted on the
 * transaction's assessment (as JSON) so history can show why a decision was made.
 */
public record RiskFindingDto(
    String code, String title, String description, int points, boolean triggered) {}
