package com.guardpay.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.device.dto.DeviceCallStateRequestDto;
import com.guardpay.device.dto.DeviceCallStateResponseDto;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Device intake with mocked repositories. Credential values are never asserted. */
@ExtendWith(MockitoExtension.class)
class DeviceSignalServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock private DeviceCallStateRepository states;
  @Mock private DeviceService devices;

  private DeviceSignalService service;
  private User user;
  private Device device;

  @BeforeEach
  void setUp() {
    service = new DeviceSignalService(states, devices);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    device = new Device(DEVICE_ID);
    device.setUser(user);
    org.mockito.Mockito.lenient().when(devices.authenticate("device-token")).thenReturn(device);
  }

  private DeviceCallStateRequestDto request(boolean activeCall) {
    DeviceCallStateRequestDto dto = new DeviceCallStateRequestDto();
    dto.setDeviceId(DEVICE_ID);
    dto.setActiveCall(activeCall);
    dto.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return dto;
  }

  @Test
  void newDeviceStateCreatesOwnerScopedRecord() {
    when(states.findByUserIdAndDeviceId(USER_ID, DEVICE_ID)).thenReturn(Optional.empty());
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DeviceCallStateResponseDto response = service.report("device-token", request(true));

    assertEquals(DEVICE_ID, response.deviceId());
    assertTrue(response.activeCall());
    verify(states).save(any());
    verify(devices).markSeen(device);
  }

  @Test
  void existingDeviceStateUpdates() {
    DeviceCallState stored = new DeviceCallState();
    stored.setUser(user);
    stored.setDeviceId(DEVICE_ID);
    stored.setActiveCall(true);
    stored.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(states.findByUserIdAndDeviceId(USER_ID, DEVICE_ID)).thenReturn(Optional.of(stored));
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DeviceCallStateResponseDto response = service.report("device-token", request(false));

    assertFalse(response.activeCall());
    assertEquals(DEVICE_ID, stored.getDeviceId());
  }

  @Test
  void staleObservationCannotOverwriteANewerState() {
    OffsetDateTime storedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    DeviceCallState stored = new DeviceCallState();
    stored.setUser(user);
    stored.setDeviceId(DEVICE_ID);
    stored.setActiveCall(true);
    stored.setObservedAt(storedAt);
    when(states.findByUserIdAndDeviceId(USER_ID, DEVICE_ID)).thenReturn(Optional.of(stored));

    DeviceCallStateRequestDto stale = request(false);
    stale.setObservedAt(storedAt.minusMinutes(5));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.report("device-token", stale));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertTrue(stored.isActiveCall());
    assertEquals(storedAt, stored.getObservedAt());
    verify(states, never()).save(any());
  }

  @Test
  void futureObservationIsClampedToNow() {
    when(states.findByUserIdAndDeviceId(USER_ID, DEVICE_ID)).thenReturn(Optional.empty());
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DeviceCallStateRequestDto future = request(true);
    future.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

    DeviceCallStateResponseDto response = service.report("device-token", future);

    assertFalse(response.observedAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
  }

  @Test
  void deviceCredentialMayNotReportAnotherDeviceId() {
    DeviceCallStateRequestDto spoofed = request(true);
    spoofed.setDeviceId(UUID.randomUUID());

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.report("device-token", spoofed));

    assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
    verify(states, never()).save(any());
    verify(states, never()).findByUserIdAndDeviceId(any(), any());
  }

  @Test
  void invalidOrRevokedCredentialIsUnauthorized() {
    when(devices.authenticate("revoked-token"))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credential"));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.report("revoked-token", request(true)));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(states, never()).save(any());
  }
}
