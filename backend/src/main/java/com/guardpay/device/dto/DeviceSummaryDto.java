package com.guardpay.device.dto;

import com.guardpay.device.Device;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Safe device metadata; never includes the credential or its hash. */
public record DeviceSummaryDto(
    UUID deviceId,
    String label,
    OffsetDateTime createdAt,
    OffsetDateTime lastSeenAt,
    OffsetDateTime revokedAt) {

  public static DeviceSummaryDto from(Device device) {
    return new DeviceSummaryDto(
        device.getId(),
        device.getLabel(),
        device.getCreatedAt(),
        device.getLastSeenAt(),
        device.getRevokedAt());
  }
}
