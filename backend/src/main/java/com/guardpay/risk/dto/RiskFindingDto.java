package com.guardpay.risk.dto;

/** One evaluated signal, mirroring the engine finding. Never persisted. */
public record RiskFindingDto(
    String code, String title, String description, int points, boolean triggered) {}
