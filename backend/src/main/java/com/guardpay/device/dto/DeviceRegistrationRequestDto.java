package com.guardpay.device.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Registers a device under the signed-in account. The id is optional. */
public class DeviceRegistrationRequestDto {

  private UUID deviceId;

  @Size(max = 120)
  private String label;

  public UUID getDeviceId() {
    return deviceId;
  }

  public void setDeviceId(UUID deviceId) {
    this.deviceId = deviceId;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
