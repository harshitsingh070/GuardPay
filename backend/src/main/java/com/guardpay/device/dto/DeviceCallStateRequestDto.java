package com.guardpay.device.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Device-reported call state. The device may report here; it may never send
 * this to the payment risk endpoint (which has no such field).
 */
public class DeviceCallStateRequestDto {

  @NotNull(message = "deviceId is required")
  private UUID deviceId;

  private boolean activeCall;

  private OffsetDateTime observedAt;

  public UUID getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(UUID deviceId) {
    this.deviceId = deviceId;
  }

  public boolean isActiveCall() {
    return activeCall;
  }

  public void setActiveCall(boolean activeCall) {
    this.activeCall = activeCall;
  }

  public OffsetDateTime getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(OffsetDateTime observedAt) {
    this.observedAt = observedAt;
  }
}
