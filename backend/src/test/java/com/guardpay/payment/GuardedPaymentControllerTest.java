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
import com.guardpay.payment.dto.GuardedPaymentRequestDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedPaypalOrderDto;
import com.guardpay.payment.dto.GuardedPaymentResponseDto.GuardedRiskDto;
import com.guardpay.payment.dto.GuardedVerificationDto;
import com.guardpay.payment.dto.VerificationConfirmResponseDto;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
@Import(CorsConfig.class)
class GuardedPaymentControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PaymentSafetyOrchestrator orchestrator;
  @MockBean private PaymentVerificationService verificationService;

  private static String body(String amount) {
    return "{\"amount\": "
        + amount
        + ", \"currency\": \"USD\", \"description\": \"Invoice payment\","
        + " \"contentText\": \"Monthly software subscription invoice for $45.\","
        + " \"recipientEmail\": \"merchant@example.com\"}";
  }

  private static GuardedPaymentResponseDto allowed() {
    return new GuardedPaymentResponseDto(
        UUID.randomUUID(),
        new GuardedRiskDto(10, RiskLevel.LOW, RecommendedAction.ALLOW, "Ok.", List.of(), List.of(),
            "NOT_REQUESTED"),
        GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
        new GuardedPaypalOrderDto("O-1", "https://www.sandbox.paypal.com/checkoutnow?token=O-1"),
        null);
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
  void verifyDecisionReturnsControlledChallengeAndNoOrder() throws Exception {
    GuardedVerificationDto challenge =
        new GuardedVerificationDto(
            UUID.randomUUID(),
            VerificationStatus.PENDING,
            OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),
            3,
            true,
            "Read the code from the server-side dev outbox.");
    when(orchestrator.initiate(any(), any()))
        .thenReturn(
            new GuardedPaymentResponseDto(
                UUID.randomUUID(),
                new GuardedRiskDto(60, RiskLevel.MEDIUM, RecommendedAction.VERIFY, "Verify.", List.of(),
                    List.of(), "NOT_REQUESTED"),
                GuardedPaymentStatus.VERIFICATION_REQUIRED,
                null,
                challenge));

    mockMvc
        .perform(
            post("/api/payments/guarded")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("45.00")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentStatus").value("VERIFICATION_REQUIRED"))
        .andExpect(jsonPath("$.paypalOrder").doesNotExist())
        .andExpect(jsonPath("$.verification.simulated").value(true))
        .andExpect(jsonPath("$.verification.status").value("PENDING"))
        .andExpect(jsonPath("$.verification.challengeCode").doesNotExist());
  }

  @Test
  void confirmVerificationReturnsOrderOnSuccess() throws Exception {
    UUID paymentId = UUID.randomUUID();
    when(verificationService.confirm(eq(paymentId), eq("123456")))
        .thenReturn(
            new VerificationConfirmResponseDto(
                paymentId,
                VerificationStatus.VERIFIED,
                GuardedPaymentStatus.PAYPAL_AUTHORIZATION_REQUIRED,
                new GuardedPaypalOrderDto("O-VER", "https://sandbox.paypal.com/approve"),
                OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                3,
                "Verification complete."));

    mockMvc
        .perform(
            post("/api/payments/" + paymentId + "/verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
        .andExpect(jsonPath("$.paymentStatus").value("PAYPAL_AUTHORIZATION_REQUIRED"))
        .andExpect(jsonPath("$.paypalOrder.orderId").value("O-VER"));
  }

  @Test
  void confirmVerificationRequiresCode() throws Exception {
    mockMvc
        .perform(
            post("/api/payments/" + UUID.randomUUID() + "/verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"\"}"))
        .andExpect(status().isBadRequest());
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
    // not break the endpoint â€” Jackson ignores them and the service decides.
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
