package com.guardpay.ai;

import com.guardpay.ai.dto.AiAnalysisRequest;
import com.guardpay.ai.dto.AiAnalysisResponse;
import com.guardpay.common.web.OutboundCallLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Internal client for the Python AI service ({@code POST /api/v1/analyze}).
 * Every failure mode — disabled, unreachable, slow, rejected, malformed,
 * circuit-open — becomes {@link AiUnavailableException} so callers degrade to
 * deterministic signals without holding the payment request for 30 seconds.
 */
@Component
public class AiAnalysisClient {

  private final AiServiceProperties properties;
  private final RestClient restClient;
  private final Object breakerLock = new Object();
  private int consecutiveFailures;
  private long openUntilMillis;

  @Autowired
  public AiAnalysisClient(AiServiceProperties properties) {
    this(properties, buildRestClient(properties));
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
    if (circuitOpen()) {
      throw new AiUnavailableException("AI circuit is open");
    }
    String url = properties.getBaseUrl() + "/api/v1/analyze";
    long startedAt = OutboundCallLogger.request("AI", "POST", url, request);
    try {
      RestClient.RequestBodySpec call =
          restClient
              .post()
              .uri("/api/v1/analyze")
              .contentType(MediaType.APPLICATION_JSON);
      if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
        call.header("X-GuardPay-Internal-Token", properties.getApiKey());
      }
      AiAnalysisResponse response =
          call.body(request)
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
      recordSuccess();
      OutboundCallLogger.success("AI", "POST", url, startedAt, response);
      return response;
    } catch (AiUnavailableException e) {
      recordFailure();
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw e;
    } catch (ResourceAccessException e) {
      recordFailure();
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw new AiUnavailableException("AI service is unreachable or timed out");
    } catch (RestClientException e) {
      recordFailure();
      OutboundCallLogger.failure("AI", "POST", url, startedAt, -1, e);
      throw new AiUnavailableException("AI service returned an unreadable response");
    }
  }

  private boolean circuitOpen() {
    synchronized (breakerLock) {
      return System.currentTimeMillis() < openUntilMillis;
    }
  }

  private void recordSuccess() {
    synchronized (breakerLock) {
      consecutiveFailures = 0;
      openUntilMillis = 0;
    }
  }

  private void recordFailure() {
    synchronized (breakerLock) {
      consecutiveFailures++;
      if (consecutiveFailures >= Math.max(1, properties.getFailureThreshold())) {
        openUntilMillis =
            System.currentTimeMillis()
                + Math.max(1, properties.getOpenSeconds()) * 1000L;
      }
    }
  }

  private static RestClient buildRestClient(AiServiceProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.max(100, properties.getConnectTimeoutMillis()));
    factory.setReadTimeout(Math.max(100, properties.getReadTimeoutMillis()));
    return RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
  }
}
