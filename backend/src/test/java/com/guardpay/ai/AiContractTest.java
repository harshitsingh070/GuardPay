package com.guardpay.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cross-language contract: the canonical fixtures under /contracts are also
 * validated by the Python service tests. The Java side must emit and consume
 * exactly that snake_case shape.
 */
class AiContractTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void requestSerializesToCanonicalSnakeCaseFixture() throws Exception {
    AiAnalysisRequest request =
        new AiAnalysisRequest(
            new AiAnalysisRequest.AiTransactionContext(
                new BigDecimal("2499.00"),
                "USD",
                "Example",
                "Someone",
                true,
                true,
                true),
            new AiAnalysisRequest.AiContentContext(
                "Pay now.", "https://example.com/pay"),
            new AiAnalysisRequest.AiDeviceContext(true));

    JsonNode actual = mapper.valueToTree(request);
    JsonNode expected =
        mapper.readTree(Files.readString(Path.of("..", "contracts", "ai-analysis-request.json")));

    assertEquals(expected.toString(), actual.toString());
  }

  @Test
  void responseDeserializesFromCanonicalSnakeCaseFixture() throws Exception {
    String json =
        Files.readString(Path.of("..", "contracts", "ai-analysis-response.json"));

    AiAnalysisResponse response = mapper.readValue(json, AiAnalysisResponse.class);

    assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), response.analysisId());
    assertEquals(1, response.findings().size());
    assertEquals("URGENT_LANGUAGE", response.findings().get(0).code());
    assertTrue(response.summary().contains("1 scam indicator"));
  }
}
