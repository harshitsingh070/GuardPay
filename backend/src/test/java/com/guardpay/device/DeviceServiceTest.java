package com.guardpay.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.common.security.TokenHasher;
import com.guardpay.device.dto.DeviceRegistrationResponseDto;
import com.guardpay.device.dto.DeviceSummaryDto;
import com.guardpay.user.DemoUserService;
import com.guardpay.user.User;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID DEVICE_ID = UUID.randomUUID();

  @Mock private DeviceRepository devices;
  @Mock private DeviceCallStateRepository states;
  @Mock private DemoUserService currentUser;

  private DeviceService service;
  private User user;

  @BeforeEach
  void setUp() {
    service = new DeviceService(devices, states, currentUser);
    user = new User();
    ReflectionTestUtils.setField(user, "id", USER_ID);
    org.mockito.Mockito.lenient().when(currentUser.currentUser()).thenReturn(user);
    org.mockito.Mockito.lenient()
        .when(devices.save(any(Device.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void registrationReturnsRawTokenOnceAndStoresOnlyItsHash() {
    when(devices.existsById(DEVICE_ID)).thenReturn(false);

    DeviceRegistrationResponseDto issued = service.register(DEVICE_ID, "  Pixel  ");

    ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
    verify(devices).save(saved.capture());
    assertEquals(DEVICE_ID, issued.deviceId());
    assertEquals("Pixel", issued.label());
    assertNotNull(issued.deviceToken());
    assertNotEquals(issued.deviceToken(), saved.getValue().getCredentialHash());
    assertEquals(TokenHasher.sha256Hex(issued.deviceToken()), saved.getValue().getCredentialHash());
    assertEquals(USER_ID, saved.getValue().getUser().getId());
  }

  @Test
  void duplicateDeviceIdCannotBeClaimedByAnotherAccount() {
    when(devices.existsById(DEVICE_ID)).thenReturn(true);

    ResponseStatusException failure =
        assertThrows(
            ResponseStatusException.class, () -> service.register(DEVICE_ID, "Phone"));

    assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    verify(devices, never()).save(any());
  }

  @Test
  void rotationReplacesTheCredentialHash() {
    Device device = new Device(DEVICE_ID);
    device.setUser(user);
    device.setCredentialHash(TokenHasher.sha256Hex("old-token"));
    when(devices.findByIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.of(device));

    DeviceRegistrationResponseDto issued = service.rotate(DEVICE_ID);

    assertNotEquals("old-token", issued.deviceToken());
    assertEquals(TokenHasher.sha256Hex(issued.deviceToken()), device.getCredentialHash());
    verify(devices).save(device);
  }

  @Test
  void revokeMarksTheDeviceAndDropsItsCallState() {
    Device device = new Device(DEVICE_ID);
    device.setUser(user);
    when(devices.findByIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.of(device));

    service.revoke(DEVICE_ID);

    assertTrue(device.isRevoked());
    verify(devices).save(device);
    verify(states).deleteByUserIdAndDeviceId(USER_ID, DEVICE_ID);
  }

  @Test
  void unknownOrOtherUsersDeviceIsNotFound() {
    when(devices.findByIdAndUserId(DEVICE_ID, USER_ID)).thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.revoke(DEVICE_ID));

    assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
    verify(states, never()).deleteByUserIdAndDeviceId(any(), any());
  }

  @Test
  void invalidCredentialIsUnauthorized() {
    when(devices.findByCredentialHashAndRevokedAtIsNull(TokenHasher.sha256Hex("bad")))
        .thenReturn(Optional.empty());

    ResponseStatusException failure =
        assertThrows(ResponseStatusException.class, () -> service.authenticate("bad"));

    assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
  }

  @Test
  void listIsScopedToTheAuthenticatedOwner() {
    Device device = new Device(DEVICE_ID);
    device.setUser(user);
    device.setLabel("Pixel");
    device.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(devices.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(device));

    List<DeviceSummaryDto> result = service.list();

    assertEquals(1, result.size());
    assertEquals(DEVICE_ID, result.get(0).deviceId());
    assertEquals("Pixel", result.get(0).label());
  }
}
