package com.guardpay.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** AI HTTP boundary with a mock server — safe mapping and safe failures. */
class AiAnalysisClientTest {

  private static final String BASE_URL = "http://localhost:8001";

  private AiServiceProperties properties;
  private RestClient.Builder builder;
  private MockRestServiceServer server;
  private AiAnalysisClient client;

  @BeforeEach
  void setUp() {
    properties = new AiServiceProperties();
    builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new AiAnalysisClient(properties, builder.build());
  }

  private AiAnalysisRequest request() {
    return new AiAnalysisRequest(
        new AiAnalysisRequest.AiTransactionContext(
            new BigDecimal("2499"), "USD", "Example", "Someone", true, true, true),
        new AiAnalysisRequest.AiContentContext("Pay now.", null),
        new AiAnalysisRequest.AiDeviceContext(null));
  }

  @Test
  void successMapsResponse() {
    server
        .expect(requestTo(BASE_URL + "/api/v1/analyze"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andRespond(
            withSuccess(
                "{\"analysisId\": \"123e4567-e89b-12d3-a456-426614174000\","
                    + " \"findings\": [{\"code\": \"URGENT_LANGUAGE\", \"title\": \"T\","
                    + " \"description\": \"D\", \"severity\": \"HIGH\","
                    + " \"confidence\": 0.9, \"evidence\": \"E\"}], \"summary\": \"S\"}",
                MediaType.APPLICATION_JSON));

    AiAnalysisResponse response = client.analyze(request());

    assertEquals(1, response.findings().size());
    assertEquals("URGENT_LANGUAGE", response.findings().get(0).code());
    assertEquals(0.9, response.findings().get(0).confidence());
    server.verify();
  }

  @Test
  void serverErrorBecomesUnavailable() {
    server
        .expect(requestTo(BASE_URL + "/api/v1/analyze"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError());

    AiUnavailableException failure =
        assertThrows(AiUnavailableException.class, () -> client.analyze(request()));

    assertTrue(failure.getMessage().contains("unreachable")
        || failure.getMessage().contains("unreadable")
        || failure.getMessage().contains("rejected"));
  }

  @Test
  void disabledClientNeverCalls() {
    properties.setEnabled(false);

    assertThrows(AiUnavailableException.class, () -> client.analyze(request()));
    server.verify();
  }
}
