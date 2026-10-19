package com.guardpay.device.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Stored device state echo. Contains the binary signal only. */
public record DeviceCallStateResponseDto(UUID deviceId, boolean activeCall, OffsetDateTime observedAt) {}
