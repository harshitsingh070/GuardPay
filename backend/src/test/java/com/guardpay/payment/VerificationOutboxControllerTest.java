package com.guardpay.payment;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Local-only developer outbox; codes must never be reachable off-host. */
@WebMvcTest(VerificationOutboxController.class)
@Import(CorsConfig.class)
class VerificationOutboxControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SimulatedVerificationOutbox outbox;
  @MockBean private PaymentVerificationRepository verifications;

  @Test
  void loopbackCanReadPendingCode() throws Exception {
    UUID paymentId = UUID.randomUUID();
    PaymentVerification verification = new PaymentVerification();
    verification.setStatus(VerificationStatus.PENDING);
    verification.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

    when(outbox.enabled()).thenReturn(true);
    when(verifications.findByTransactionId(paymentId)).thenReturn(Optional.of(verification));
    when(outbox.read(paymentId)).thenReturn(Optional.of("123456"));

    mockMvc
        .perform(get("/api/dev/verifications/{paymentId}/code", paymentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
        .andExpect(jsonPath("$.code").value("123456"));
  }

  @Test
  void nonLoopbackCallerIsRejectedEvenWithTheRightId() throws Exception {
    UUID paymentId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/dev/verifications/{paymentId}/code", paymentId)
                .with(
                    request -> {
                      request.setRemoteAddr("203.0.113.10");
                      return request;
                    }))
        .andExpect(status().isNotFound());
  }

  @Test
  void expiredOrDisabledOutboxReturnsNotFound() throws Exception {
    UUID paymentId = UUID.randomUUID();
    PaymentVerification verification = new PaymentVerification();
    verification.setStatus(VerificationStatus.PENDING);
    verification.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));

    when(outbox.enabled()).thenReturn(true);
    when(verifications.findByTransactionId(paymentId)).thenReturn(Optional.of(verification));

    mockMvc
        .perform(get("/api/dev/verifications/{paymentId}/code", paymentId))
        .andExpect(status().isNotFound());
  }
}
