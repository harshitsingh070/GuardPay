package com.guardpay.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.device.dto.DeviceCallStateRequestDto;
import com.guardpay.device.dto.DeviceCallStateResponseDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Device endpoint with a mocked service: header passthrough and shapes only. */
@WebMvcTest(DeviceSignalController.class)
@Import(CorsConfig.class)
class DeviceSignalControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private DeviceSignalService service;

  private static final UUID DEVICE_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  @Test
  void validReportIsAccepted() throws Exception {
    when(service.report(eq("dev-token-123"), any(DeviceCallStateRequestDto.class)))
        .thenReturn(new DeviceCallStateResponseDto(DEVICE_ID, true, OffsetDateTime.now(ZoneOffset.UTC)));

    mockMvc
        .perform(
            post("/api/device-signals/call-state")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GuardPay-Device-Token", "dev-token-123")
                .content(
                    "{\"deviceId\": \"123e4567-e89b-12d3-a456-426614174000\","
                        + " \"activeCall\": true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deviceId").value(DEVICE_ID.toString()))
        .andExpect(jsonPath("$.activeCall").value(true));

    ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
    verify(service).report(token.capture(), any(DeviceCallStateRequestDto.class));
    assertEquals("dev-token-123", token.getValue());
  }

  @Test
  void invalidTokenMapsToUnauthorized() throws Exception {
    when(service.report(eq("bad"), any(DeviceCallStateRequestDto.class)))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token"));

    mockMvc
        .perform(
            post("/api/device-signals/call-state")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GuardPay-Device-Token", "bad")
                .content(
                    "{\"deviceId\": \"123e4567-e89b-12d3-a456-426614174000\","
                        + " \"activeCall\": true}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void missingDeviceIdIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/device-signals/call-state")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GuardPay-Device-Token", "dev-token-123")
                .content("{\"activeCall\": true}"))
        .andExpect(status().isBadRequest());
  }
}
