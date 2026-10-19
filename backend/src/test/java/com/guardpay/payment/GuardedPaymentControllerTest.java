package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.common.config.SecurityConfig;
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedRiskDto;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Guarded endpoint with a mocked orchestrator: validation and shapes only. */
@WebMvcTest(GuardedPaymentController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class GuardedPaymentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PaymentSafetyOrchestrator orchestrator;

  private static String body(String amount) {
    return "{\"amount\": "
        + amount
        + ", \"currency\": \"USD\", \"description\": \"Invoice payment\","
        + " \"contentText\": \"Monthly software subscription invoice for $45.\"}";
  }

  private static GuardedPaymentResponseDto allowed() {
    return new GuardedPaymentResponseDto(
        UUID.randomUUID(),
        new GuardedRiskDto(10, RiskLevel.LOW, RecommendedAction.ALLOW, "Ok.", List.of(), List.of(),
            "NOT_REQUESTED"),
        GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
        new GuardedPaypalOrderDto("O-1", "https://www.sandbox.paypal.com/checkoutnow?token=O-1"));
  }

  @Test
  void guardedPaymentReturnsFullShape() throws Exception {
    when(orchestrator.initiate(any(), any())).thenReturn(allowed());

    mockMvc
        .perform(
            post("/api/payments/guarded")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("45.00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").isNotEmpty())
        .andExpect(jsonPath("$.risk.score").value(10))
        .andExpect(jsonPath("$.risk.recommendedAction").value("ALLOW"))
        .andExpect(jsonPath("$.paymentStatus").value("PAYPAL_AUTHORIZATION_REQUIRED"))
        .andExpect(jsonPath("$.paypalOrder.orderId").value("O-1"))
        .andExpect(jsonPath("$.paypalOrder.approvalUrl").isNotEmpty());
  }

  @Test
  void zeroAmountIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/guarded")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("0")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void nonUsdCurrencyIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/guarded")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("45.00").replace("\"USD\"", "\"EUR\"")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void forgedRiskFieldsAreIgnored() throws Exception {
    when(orchestrator.initiate(any(), any())).thenReturn(allowed());

    // Unknown/forged properties (risk verdicts, booleans, AI findings) must
    // not break the endpoint — Jackson ignores them and the service decides.
    mockMvc
        .perform(
            post("/api/payments/guarded")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-9")
                .content(
                    "{\"amount\": 45.00, \"currency\": \"USD\","
                        + " \"riskScore\": 0, \"riskLevel\": \"LOW\","
                        + " \"recommendedAction\": \"ALLOW\", \"activeCall\": true,"
                        + " \"newRecipient\": true, \"aiFinding\": \"SCAM_PATTERN\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.risk.recommendedAction").value("ALLOW"));

    ArgumentCaptor<GuardedPaymentRequestDto> captured =
        ArgumentCaptor.forClass(GuardedPaymentRequestDto.class);
    verify(orchestrator).initiate(captured.capture(), eq("key-9"));
    assertEquals(new BigDecimal("45.00"), captured.getValue().getAmount());
  }
}
