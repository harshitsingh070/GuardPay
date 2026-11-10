package com.guardpay.risk;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API tests for {@code POST /api/risk/evaluate}.
 *
 * <p>Web-layer slice with the real service and engine (deterministic, no
 * mocks). No database, no AI, no PayPal, no Python required.
 */
@WebMvcTest(RiskController.class)
@Import({CorsConfig.class, RiskEvaluationService.class, DeterministicRiskEngine.class})
class RiskControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void safePaymentReturnsAllow() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 45, "historicalAverage": 87, "activeCall": false,
                     "newRecipient": false, "unknownMerchant": false,
                     "unexpectedInvoice": false, "urgentLanguage": false,
                     "suspiciousUrl": false, "socialEngineeringDetected": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(0))
        .andExpect(jsonPath("$.riskLevel").value("LOW"))
        .andExpect(jsonPath("$.recommendedAction").value("ALLOW"))
        .andExpect(jsonPath("$.explanation").isNotEmpty())
        .andExpect(jsonPath("$.findings.length()").value(33));
  }

  @Test
  void heroScenarioReturnsStop() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 2499.00, "historicalAverage": 87.00, "activeCall": true,
                     "newRecipient": true, "repeatedAttempts": true, "unknownMerchant": true,
                     "unexpectedInvoice": true, "urgentLanguage": true,
                     "suspiciousUrl": true, "socialEngineeringDetected": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(100))
        .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
        .andExpect(jsonPath("$.recommendedAction").value("STOP"))
        .andExpect(jsonPath("$.findings.length()").value(33))
        .andExpect(jsonPath("$.findings[0].code").value("ACTIVE_CALL"))
        .andExpect(jsonPath("$.findings[15].code").value("MISSING_CONTEXT"))
        .andExpect(jsonPath("$.findings[15].triggered").value(false));
  }

  @Test
  void mediumRiskScenarioReturnsVerify() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 45, "historicalAverage": 87, "activeCall": true,
                     "newRecipient": true, "unknownMerchant": false,
                     "unexpectedInvoice": true, "urgentLanguage": true,
                     "suspiciousUrl": false, "socialEngineeringDetected": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(60))
        .andExpect(jsonPath("$.riskLevel").value("MEDIUM"))
        .andExpect(jsonPath("$.recommendedAction").value("VERIFY"));
  }

  @Test
  void negativeAmountReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": -5, "historicalAverage": 87, "activeCall": false,
                     "newRecipient": false, "unknownMerchant": false,
                     "unexpectedInvoice": false, "urgentLanguage": false,
                     "suspiciousUrl": false, "socialEngineeringDetected": false}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void missingHistoricalAverageStillEvaluatesOtherSignals() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount": 9999, "activeCall": true,
                     "newRecipient": false, "unknownMerchant": false,
                     "unexpectedInvoice": false, "urgentLanguage": false,
                     "suspiciousUrl": false, "socialEngineeringDetected": false}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.score").value(15))
        .andExpect(jsonPath("$.riskLevel").value("LOW"))
        .andExpect(jsonPath("$.recommendedAction").value("ALLOW"));
  }

  @Test
  void malformedJsonReturnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/risk/evaluate")

                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }
}

