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
import org.springframework.web.server.ResponseStatusException;

/** Device intake with a mocked repository. Token values never asserted — only outcomes. */
@ExtendWith(MockitoExtension.class)
class DeviceSignalServiceTest {

  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock private DeviceCallStateRepository states;

  private DeviceSignalProperties properties;
  private DeviceSignalService service;

  @BeforeEach
  void setUp() {
    properties = new DeviceSignalProperties();
    properties.setSignalToken("dev-token-123");
    service = new DeviceSignalService(states, properties);
  }

  private DeviceCallStateRequestDto request(boolean activeCall) {
    DeviceCallStateRequestDto dto = new DeviceCallStateRequestDto();
    dto.setDeviceId(DEVICE_ID);
    dto.setActiveCall(activeCall);
    dto.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return dto;
  }

  @Test
  void newDeviceStateCreatesRecord() {
    when(states.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DeviceCallStateResponseDto response = service.report("dev-token-123", request(true));

    assertEquals(DEVICE_ID, response.deviceId());
    assertTrue(response.activeCall());
    verify(states).save(any());
  }

  @Test
  void existingDeviceStateUpdates() {
    DeviceCallState stored = new DeviceCallState();
    stored.setDeviceId(DEVICE_ID);
    stored.setActiveCall(true);
    stored.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
    when(states.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(stored));
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    DeviceCallStateResponseDto response = service.report("dev-token-123", request(false));

    assertFalse(response.activeCall());
    assertEquals(DEVICE_ID, stored.getDeviceId());
  }

  @Test
  void staleObservationCannotOverwriteANewerState() {
    OffsetDateTime storedAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1);
    DeviceCallState stored = new DeviceCallState();
    stored.setDeviceId(DEVICE_ID);
    stored.setActiveCall(true);
    stored.setObservedAt(storedAt);
    when(states.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(stored));

    DeviceCallStateRequestDto stale = request(false);
    stale.setObservedAt(storedAt.minusMinutes(5));

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.report("dev-token-123", stale));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    assertTrue(stored.isActiveCall());
    assertEquals(storedAt, stored.getObservedAt());
    verify(states, never()).save(any());
  }

  @Test
  void futureObservationIsClampedToNow() {
    when(states.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());
    when(states.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    DeviceCallStateRequestDto future = request(true);
    future.setObservedAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));

    DeviceCallStateResponseDto response = service.report("dev-token-123", future);

    assertFalse(response.observedAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
  }

  @Test
  void missingTokenIsUnauthorized() {
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.report(null, request(true)));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(states, never()).save(any());
  }

  @Test
  void incorrectTokenIsUnauthorized() {
    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.report("wrong-token", request(true)));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    verify(states, never()).save(any());
  }

  @Test
  void unconfiguredTokenRejectsEverything() {
    properties.setSignalToken(" ");

    assertThrows(ResponseStatusException.class, () -> service.report("dev-token-123", request(true)));
    verify(states, never()).findByDeviceId(any());
  }
}
