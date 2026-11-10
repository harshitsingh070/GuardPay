package com.guardpay.intelligence.dto;

import java.time.OffsetDateTime;

/** Admin review trail entry. */
public record ReviewEventDto(
    String action,
    String fromStatus,
    String toStatus,
    String note,
    OffsetDateTime createdAt) {}
