package com.guardpay.device;

import com.guardpay.device.dto.DeviceRegistrationRequestDto;
import com.guardpay.device.dto.DeviceRegistrationResponseDto;
import com.guardpay.device.dto.DeviceSummaryDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated device registration and credential lifecycle. */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

  private final DeviceService devices;

  public DeviceController(DeviceService devices) {
    this.devices = devices;
  }

  @GetMapping
  public List<DeviceSummaryDto> list() {
    return devices.list();
  }

  @PostMapping
  public ResponseEntity<DeviceRegistrationResponseDto> register(
      @Valid @RequestBody DeviceRegistrationRequestDto request) {
    DeviceRegistrationResponseDto issued =
        devices.register(request.getDeviceId(), request.getLabel());
    return ResponseEntity.status(HttpStatus.CREATED).body(issued);
  }

  @PostMapping("/{deviceId}/rotate")
  public DeviceRegistrationResponseDto rotate(@PathVariable UUID deviceId) {
    return devices.rotate(deviceId);
  }

  @DeleteMapping("/{deviceId}")
  public ResponseEntity<Void> revoke(@PathVariable UUID deviceId) {
    devices.revoke(deviceId);
    return ResponseEntity.noContent().build();
  }
}
