package com.guardpay.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.common.config.SecurityConfig;
import com.guardpay.risk.RecommendedAction;
import com.guardpay.risk.RiskLevel;
import com.guardpay.transaction.dto.RiskAssessmentViewDto;
import com.guardpay.transaction.dto.TransactionDetailDto;
import com.guardpay.transaction.dto.TransactionPageDto;
import com.guardpay.transaction.dto.TransactionSummaryDto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Read-only history endpoint with a mocked service: shapes and status codes. */
@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private TransactionQueryService service;

  @Test
  void listReturnsPagedHistory() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.list(any(), eq(0), eq(20)))
        .thenReturn(
            new TransactionPageDto(
                List.of(
                    new TransactionSummaryDto(
                        id,
                        new BigDecimal("45.00"),
                        "USD",
                        TransactionStatus.COMPLETED,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        "Invoice",
                        "Known Co",
                        null,
                        60,
                        RiskLevel.MEDIUM,
                        RecommendedAction.VERIFY)),
                0,
                20,
                1,
                1));

    mockMvc
        .perform(get("/api/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].amount").value(45.00))
        .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.items[0].riskLevel").value("MEDIUM"))
        .andExpect(jsonPath("$.totalItems").value(1));
  }

  @Test
  void detailReturnsFindingsAndReferences() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.detail(eq(id), any()))
        .thenReturn(
            new TransactionDetailDto(
                id,
                new BigDecimal("45.00"),
                "USD",
                TransactionStatus.COMPLETED,
                "Invoice",
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                "Known Co",
                null,
                "ORDER-1",
                "CAPTURE-1",
                null,
                new RiskAssessmentViewDto(
                    60, RiskLevel.MEDIUM, RecommendedAction.VERIFY, "Verify.", List.of(), null),
                null));

    mockMvc
        .perform(get("/api/transactions/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paypalOrderId").value("ORDER-1"))
        .andExpect(jsonPath("$.assessment.recommendedAction").value("VERIFY"));
  }

  @Test
  void unknownTransactionReturnsNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(service.detail(eq(id), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown transaction"));

    mockMvc.perform(get("/api/transactions/" + id)).andExpect(status().isNotFound());
  }
}
