package com.guardpay.risk.signal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.risk.dto.RiskFindingDto;
import com.guardpay.risk.signal.dto.AutomaticEvaluationResponseDto;
import com.guardpay.risk.signal.dto.PaymentSignalDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Automatic endpoint with a mocked service: shape passthrough and input
 * validation â€” never live data.
 */
@WebMvcTest(AutomaticRiskController.class)
@Import(CorsConfig.class)
class AutomaticRiskControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private AutomaticRiskEvaluationService service;

  private static String body(String amount) {
    return "{\"amount\": "
        + amount
        + ", \"recipientId\": null, \"merchantId\": null}";
  }

  @Test
  void automaticEvaluationReturnsSignalsAndFindings() throws Exception {
    when(service.evaluate(any()))
        .thenReturn(
            new AutomaticEvaluationResponseDto(
                40,
                RiskLevel.MEDIUM,
                RecommendedAction.VERIFY,
                "GuardPay flagged this payment.",
                List.of(
                    new PaymentSignalDto(
                        PaymentSignalType.UNUSUAL_AMOUNT,
                        true,
                        SignalSource.TRANSACTION_HISTORY,
                        null,
                        "2499 is at least 5x the historical average of 87.")),
                List.of(
                    new RiskFindingDto("NEW_RECIPIENT", "New recipient", "New.", 20, true)),
                "NOT_REQUESTED", null));

    mockMvc
        .perform(
            post("/api/risk/evaluate-automatic")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("2499")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(40))
        .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
        .andExpect(jsonPath("$.recommendedAction").value("VERIFY"))
        .andExpect(jsonPath("$.automaticSignals.length()").value(1))
        .andExpect(jsonPath("$.automaticSignals[0].type").value("UNUSUAL_AMOUNT"))
        .andExpect(jsonPath("$.findings.length()").value(1))
        .andExpect(jsonPath("$.aiStatus").value("NOT_REQUESTED"));
  }

  @Test
  void missingAmountIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate-automatic")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
