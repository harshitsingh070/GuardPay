package com.guardpay.device.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Registration/rotation result. The raw credential is returned exactly once
 * and is never stored or logged by the backend.
 */
public record DeviceRegistrationResponseDto(
    UUID deviceId, String deviceToken, String label, OffsetDateTime createdAt) {}
