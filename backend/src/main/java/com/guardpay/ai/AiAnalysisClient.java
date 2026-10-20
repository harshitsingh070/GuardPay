package com.guardpay.ai;

import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.common.web.OutboundCallLogger;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal client for the Python AI service ({@code POST /api/v1/analyze}).
 * Every failure mode — disabled, unreachable, rejected, malformed — becomes
 * {@link AiUnavailableException} so callers degrade to deterministic signals.
 */
@Component
public class AiAnalysisClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private final AiServiceProperties properties;
  private final RestClient restClient;

  @Autowired
  public AiAnalysisClient(AiServiceProperties properties) {
    this(properties, buildRestClient(properties.getBaseUrl()));
  }

  /** Test hook: inject mock-server client. */
  AiAnalysisClient(AiServiceProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  public AiAnalysisResponse analyze(AiAnalysisRequest request) {
    if (!properties.isEnabled()) {
      throw new AiUnavailableException("AI analysis is disabled");
    }
    String url = properties.getBaseUrl() + "/api/v1/analyze";
    long startedAt = OutboundCallLogger.request("AI", "POST", url, request);
    try {
      AiAnalysisResponse response =
          restClient
              .post()
              .uri("/api/v1/analyze")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .onStatus(
                  status -> status.value() == 503,
                  (req, res) -> {
                    throw new AiUnavailableException("AI service is not configured");
                  })
              .onStatus(
                  status -> status.value() >= 400,
                  (req, res) -> {
                    throw new AiUnavailableException(
                        "AI service rejected the request (HTTP " + res.getStatusCode().value() + ")");
                  })
              .body(AiAnalysisResponse.class);
      if (response == null || response.findings() == null) {
        throw new AiUnavailableException("AI service returned an empty response");
      }
      OutboundCallLogger.success("AI", "POST", url, startedAt, response);
      return response;
    } catch (AiUnavailableException e) {
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw e;
    } catch (ResourceAccessException e) {
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw new AiUnavailableException("AI service is unreachable");
    } catch (RestClientException e) {
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw new AiUnavailableException("AI service returned an unreadable response");
    }
  }

  private static RestClient buildRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }
}
