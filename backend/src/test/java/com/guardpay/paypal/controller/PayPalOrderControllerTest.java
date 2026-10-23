package com.guardpay.paypal.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.payment.GuardedPaymentStatus;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.exception.CaptureBlockedException;
import com.guardpay.paypal.service.PayPalOrderService;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Capture endpoint with a mocked service. Order creation is intentionally not
 * exposed: every order must originate from the guarded-payment orchestrator.
 */
@WebMvcTest(PayPalOrderController.class)
@Import(CorsConfig.class)
class PayPalOrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PayPalOrderService service;

  @Test
  void legacyPublicOrderCreationIsNotExposed() throws Exception {
    mockMvc
        .perform(
            post("/api/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"amount\": 45.00, \"currency\": \"USD\","
                        + " \"activeCall\": false, \"newRecipient\": false}"))
        // The route is no longer mapped, so the legacy public flow is inaccessible.
        .andExpect(status().isNotFound());
  }

  @Test
  void captureReturnsCaptureShape() throws Exception {
    when(service.captureOrder("ORDER-1"))
        .thenReturn(
            new CaptureOrderResponseDto(
                "ORDER-1", "COMPLETED", "CAP-1", new BigDecimal("45.00"), "USD",
                GuardedPaymentStatus.COMPLETED));

    mockMvc
        .perform(post("/api/paypal/orders/ORDER-1/capture"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER-1"))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.captureId").value("CAP-1"))
        .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"));
  }

  @Test
  void blockedCaptureReturnsGateShape() throws Exception {
    when(service.captureOrder("ORDER-9"))
        .thenThrow(
            new CaptureBlockedException(
                "GuardPay stopped this payment before capture.",
                RecommendedAction.STOP,
                RiskLevel.CRITICAL,
                "ORDER-9"));

    mockMvc
        .perform(post("/api/paypal/orders/ORDER-9/capture"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.action").value("STOP"))
        .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
        .andExpect(jsonPath("$.orderId").value("ORDER-9"));
  }
}
