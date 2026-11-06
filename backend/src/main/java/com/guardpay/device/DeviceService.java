package com.guardpay.device;

import com.guardpay.common.security.TokenHasher;
import com.guardpay.device.dto.DeviceRegistrationResponseDto;
import com.guardpay.device.dto.DeviceSummaryDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Registers, rotates, revokes, and authenticates account-owned devices. */
@Service
public class DeviceService {

  private final DeviceRepository devices;
  private final DeviceCallStateRepository states;
  private final DemoUserService currentUser;

  public DeviceService(
      DeviceRepository devices, DeviceCallStateRepository states, DemoUserService currentUser) {
    this.devices = devices;
    this.states = states;
    this.currentUser = currentUser;
  }

  @Transactional
  public DeviceRegistrationResponseDto register(UUID requestedId, String label) {
    User user = currentUser.currentUser();
    if (requestedId != null && devices.existsById(requestedId)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This device is already registered; rotate its credential instead");
    }
    Device device = requestedId == null ? new Device() : new Device(requestedId);
    device.setUser(user);
    device.setLabel(normalizeLabel(label));
    String token = TokenHasher.randomToken();
    device.setCredentialHash(TokenHasher.sha256Hex(token));
    Device saved = devices.save(device);
    return new DeviceRegistrationResponseDto(
        saved.getId(), token, saved.getLabel(), saved.getCreatedAt());
  }

  @Transactional
  public DeviceRegistrationResponseDto rotate(UUID deviceId) {
    Device device = requireOwned(deviceId);
    if (device.isRevoked()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This device is revoked; register a new device");
    }
    String token = TokenHasher.randomToken();
    device.setCredentialHash(TokenHasher.sha256Hex(token));
    Device saved = devices.save(device);
    return new DeviceRegistrationResponseDto(
        saved.getId(), token, saved.getLabel(), saved.getCreatedAt());
  }

  @Transactional
  public void revoke(UUID deviceId) {
    Device device = requireOwned(deviceId);
    if (!device.isRevoked()) {
      device.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
      devices.save(device);
    }
    states.deleteByUserIdAndDeviceId(device.getUser().getId(), deviceId);
  }

  @Transactional(readOnly = true)
  public List<DeviceSummaryDto> list() {
    UUID userId = currentUser.currentUser().getId();
    return devices.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(DeviceSummaryDto::from)
        .toList();
  }

  /** Resolves a raw device credential to its active owner; never logs the token. */
  @Transactional(readOnly = true)
  public Device authenticate(String token) {
    if (token == null || token.isBlank() || token.length() > 512) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credential");
    }
    return devices
        .findByCredentialHashAndRevokedAtIsNull(TokenHasher.sha256Hex(token))
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid device credential"));
  }

  @Transactional
  public void markSeen(Device device) {
    device.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
    devices.save(device);
  }

  private Device requireOwned(UUID deviceId) {
    UUID userId = currentUser.currentUser().getId();
    return devices
        .findByIdAndUserId(deviceId, userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
  }

  private static String normalizeLabel(String label) {
    if (label == null || label.isBlank()) {
      return null;
    }
    return label.trim();
  }
}
