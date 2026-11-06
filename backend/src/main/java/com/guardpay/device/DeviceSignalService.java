package com.guardpay.device;

import com.guardpay.device.dto.DeviceCallStateRequestDto;
import com.guardpay.device.dto.DeviceCallStateResponseDto;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Device signal intake: per-device credential, then a monotonic upsert of the
 * latest state for that credential's owner. Future-dated observations are
 * clamped to now; older observations are rejected so a replay cannot erase a
 * newer active-call state. Tokens are never logged.
 */
@Service
public class DeviceSignalService {

  private static final Logger log = LoggerFactory.getLogger(DeviceSignalService.class);

  /** Observations more than this far in the future are treated as clock drift. */
  private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

  private final DeviceCallStateRepository states;
  private final DeviceService devices;

  public DeviceSignalService(DeviceCallStateRepository states, DeviceService devices) {
    this.states = states;
    this.devices = devices;
  }

  @Transactional
  public DeviceCallStateResponseDto report(String token, DeviceCallStateRequestDto dto) {
    Device device = devices.authenticate(token);
    if (!device.getId().equals(dto.getDeviceId())) {
      // A valid credential may only report the state of its own device.
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Device credential does not match the reported device");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime observedAt = dto.getObservedAt() != null ? dto.getObservedAt() : now;
    if (observedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
      observedAt = now;
    }

    DeviceCallState state =
        states
            .findByUserIdAndDeviceId(device.getUser().getId(), dto.getDeviceId())
            .orElseGet(DeviceCallState::new);
    if (state.getDeviceId() == null) {
      state.setUser(device.getUser());
      state.setDeviceId(dto.getDeviceId());
    } else if (state.getObservedAt() != null && observedAt.isBefore(state.getObservedAt())) {
      // Old reports must not overwrite a newer observation (replay/clock skew).
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale device observation");
    }
    state.setActiveCall(dto.isActiveCall());
    state.setObservedAt(observedAt);
    DeviceCallState saved = states.save(state);
    devices.markSeen(device);

    log.info("Device call state stored (active={})", saved.isActiveCall());
    return new DeviceCallStateResponseDto(
        saved.getDeviceId(), saved.isActiveCall(), saved.getObservedAt());
  }
}
