package com.guardpay.paypal.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.common.config.SecurityConfig;
import com.guardpay.paypal.dto.CaptureOrderResponseDto;
import com.guardpay.paypal.dto.CreateOrderResponseDto;
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
 * Order endpoints with a mocked service. Covers validation shapes and the
 * 403 gate contract — never live PayPal.
 */
@WebMvcTest(PayPalOrderController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class PayPalOrderControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PayPalOrderService service;

  private static String orderBody(String amount, String currency) {
    return "{\"amount\": "
        + amount
        + ", \"currency\": \""
        + currency
        + "\", \"description\": \"GuardPay protected payment\","
        + " \"historicalAverage\": 87, \"activeCall\": false, \"newRecipient\": false,"
        + " \"unknownMerchant\": false, \"unexpectedInvoice\": false,"
        + " \"urgentLanguage\": false, \"suspiciousUrl\": false,"
        + " \"socialEngineeringDetected\": false}";
  }

  @Test
  void createOrderReturnsOrderShape() throws Exception {
    when(service.createOrder(any()))
        .thenReturn(
            new CreateOrderResponseDto(
                "ORDER-1",
                "CREATED",
                "https://www.sandbox.paypal.com/checkoutnow?token=ORDER-1",
                new BigDecimal("45.00"),
                "USD",
                RiskLevel.LOW,
                RecommendedAction.ALLOW,
                true,
                "PayPal order created."));

    mockMvc
        .perform(
            post("/api/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("45.00", "USD")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER-1"))
        .andExpect(jsonPath("$.status").value("CREATED"))
        .andExpect(jsonPath("$.approvalUrl").isNotEmpty())
        .andExpect(jsonPath("$.allowed").value(true));
  }

  @Test
  void zeroAmountIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("0", "USD")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonUsdCurrencyIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderBody("45.00", "EUR")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void missingAmountIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/paypal/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\": \"USD\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void captureReturnsCaptureShape() throws Exception {
    when(service.captureOrder("ORDER-1"))
        .thenReturn(
            new CaptureOrderResponseDto("ORDER-1", "COMPLETED", "CAP-1", new BigDecimal("45.00"), "USD"));

    mockMvc
        .perform(post("/api/paypal/orders/ORDER-1/capture"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value("ORDER-1"))
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.captureId").value("CAP-1"));
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
