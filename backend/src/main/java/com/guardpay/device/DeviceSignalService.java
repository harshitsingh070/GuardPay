package com.guardpay.device;

import com.guardpay.device.dto.DeviceCallStateRequestDto;
import com.guardpay.device.dto.DeviceCallStateResponseDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * Device signal intake: token check, then a monotonic upsert of the latest
 * state. Future-dated observations are clamped to now; older observations are
 * rejected so a replay cannot erase a newer active-call state. The token itself
 * is compared in constant time and never logged.
 */
@Service
public class DeviceSignalService {

  private static final Logger log = LoggerFactory.getLogger(DeviceSignalService.class);

  /** Observations more than this far in the future are treated as clock drift. */
  private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

  private final DeviceCallStateRepository states;
  private final DeviceSignalProperties properties;

  public DeviceSignalService(DeviceCallStateRepository states, DeviceSignalProperties properties) {
    this.states = states;
    this.properties = properties;
  }

  @Transactional
  public DeviceCallStateResponseDto report(String token, DeviceCallStateRequestDto dto) {
    requireValidToken(token);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime observedAt = dto.getObservedAt() != null ? dto.getObservedAt() : now;
    if (observedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
      observedAt = now;
    }

    DeviceCallState state =
        states.findByDeviceId(dto.getDeviceId()).orElseGet(DeviceCallState::new);
    if (state.getDeviceId() == null) {
      state.setDeviceId(dto.getDeviceId());
    } else if (state.getObservedAt() != null
        && observedAt.isBefore(state.getObservedAt())) {
      // Old reports must not overwrite a newer observation (replay/clock skew).
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale device observation");
    }
    state.setActiveCall(dto.isActiveCall());
    state.setObservedAt(observedAt);
    DeviceCallState saved = states.save(state);

    log.info("Device call state stored (active={})", saved.isActiveCall());
    return new DeviceCallStateResponseDto(saved.getDeviceId(), saved.isActiveCall(), saved.getObservedAt());
  }

  void requireValidToken(String token) {
    String expected = properties.getSignalToken();
    if (token == null
        || expected == null
        || expected.isBlank()
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token");
    }
  }
}
