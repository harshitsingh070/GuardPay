package com.guardpay.device;

import com.guardpay.device.dto.DeviceCallStateRequestDto;
import com.guardpay.device.dto.DeviceCallStateResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device signal intake. The {@code X-GuardPay-Device-Token} header is a
 * per-device credential issued by {@code /api/devices} — never logged. Devices
 * report call state here; payment risk stays on the risk endpoints.
 */
@RestController
@RequestMapping("/api/device-signals")
public class DeviceSignalController {

  static final String TOKEN_HEADER = "X-GuardPay-Device-Token";

  private final DeviceSignalService service;

  public DeviceSignalController(DeviceSignalService service) {
    this.service = service;
  }

  @PostMapping("/call-state")
  public ResponseEntity<DeviceCallStateResponseDto> reportCallState(
      @RequestHeader(value = TOKEN_HEADER, required = false) String token,
      @Valid @RequestBody DeviceCallStateRequestDto request) {
    return ResponseEntity.ok(service.report(token, request));
  }
}
