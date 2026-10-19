package com.guardpay.ai.dto;

/** One AI evidence finding. {@code code} must belong to the controlled vocabulary. */
public record AiFindingDto(
    String code, String title, String description, String severity, Double confidence, String evidence) {}
