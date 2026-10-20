package com.guardpay.paypal.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guardpay.common.web.OutboundCallLogger;
import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import com.guardpay.paypal.exception.PayPalException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Reusable, authenticated PayPal REST caller. Base URL from configuration,
 * bearer token from {@link PayPalOAuthService}, JSON bodies, bounded
 * timeouts — and never a credential in an exception or log line.
 */
@Component
public class PayPalClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  private static final ObjectMapper JSON = new ObjectMapper();

  private final PayPalOAuthService oauthService;
  private final RestClient restClient;
  private final String baseUrl;

  @Autowired
  public PayPalClient(PayPalOAuthService oauthService, PayPalProperties properties) {
    this(oauthService, buildRestClient(properties.getBaseUrl()), properties.getBaseUrl());
  }

  /** Test hook: inject mock-server client. */
  PayPalClient(PayPalOAuthService oauthService, RestClient restClient) {
    this(oauthService, restClient, "https://api-m.sandbox.paypal.com");
  }

  private PayPalClient(PayPalOAuthService oauthService, RestClient restClient, String baseUrl) {
    this.oauthService = oauthService;
    this.restClient = restClient;
    this.baseUrl = baseUrl;
  }

  /** Authenticated GET returning a parsed body. Safe errors only. */
  public <T> T get(String path, Class<T> responseType) {
    String url = baseUrl + path;
    long startedAt = OutboundCallLogger.request("PAYPAL", "GET", url, null);
    try {
      T result =
          restClient
              .get()
              .uri(path)
              .header("Authorization", "Bearer " + oauthService.getAccessToken())
              .retrieve()
              .onStatus(
                  status -> status.value() == 401 || status.value() == 403,
                  (request, response) -> {
                    throw new PayPalAuthenticationException(
                        authErrorMessage(response.getStatusCode().value()));
                  })
              .onStatus(
                  status -> status.value() >= 500,
                  (request, response) -> {
                    throw new PayPalException(
                        response.getStatusCode().value(),
                        unavailableMessage(response.getStatusCode().value()));
                  })
              .onStatus(
                  status -> status.value() >= 400,
                  (request, response) -> {
                    throw new PayPalException(
                        response.getStatusCode().value(), rejectedMessage(response));
                  })
              .body(responseType);
      OutboundCallLogger.success("PAYPAL", "GET", url, startedAt, result);
      return result;
    } catch (PayPalException e) {
      OutboundCallLogger.failure("PAYPAL", "GET", url, startedAt, e.getStatusCode(), e);
      throw e;
    } catch (ResourceAccessException e) {
      OutboundCallLogger.failure("PAYPAL", "GET", url, startedAt, -1, e);
      throw new PayPalException("PayPal is unreachable (network timeout)");
    }
  }

  /** Authenticated POST with a JSON body returning a parsed body. Safe errors only. */
  public <T> T post(String path, Object body, Class<T> responseType) {
    String url = baseUrl + path;
    long startedAt = OutboundCallLogger.request("PAYPAL", "POST", url, body);
    try {
      T result =
          restClient
              .post()
              .uri(path)
              .header("Authorization", "Bearer " + oauthService.getAccessToken())
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .onStatus(
                  status -> status.value() == 401 || status.value() == 403,
                  (request, response) -> {
                    throw new PayPalAuthenticationException(
                        authErrorMessage(response.getStatusCode().value()));
                  })
              .onStatus(
                  status -> status.value() >= 500,
                  (request, response) -> {
                    throw new PayPalException(
                        response.getStatusCode().value(),
                        unavailableMessage(response.getStatusCode().value()));
                  })
              .onStatus(
                  status -> status.value() >= 400,
                  (request, response) -> {
                    throw new PayPalException(
                        response.getStatusCode().value(), rejectedMessage(response));
                  })
              .body(responseType);
      OutboundCallLogger.success("PAYPAL", "POST", url, startedAt, result);
      return result;
    } catch (PayPalException e) {
      OutboundCallLogger.failure("PAYPAL", "POST", url, startedAt, e.getStatusCode(), e);
      throw e;
    } catch (ResourceAccessException e) {
      OutboundCallLogger.failure("PAYPAL", "POST", url, startedAt, -1, e);
      throw new PayPalException("PayPal is unreachable (network timeout)");
    }
  }

  private static String authErrorMessage(int status) {
    return "PayPal rejected the API credentials (HTTP " + status + ")";
  }

  private static String unavailableMessage(int status) {
    return "PayPal is temporarily unavailable (HTTP " + status + ")";
  }

  private static String rejectedMessage(ClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    String issue = issueCode(response);
    if (issue == null) {
      return "PayPal rejected the request (HTTP " + status + ")";
    }
    return switch (issue) {
      case "ORDER_NOT_APPROVED", "PAYER_ACTION_REQUIRED" ->
          "This PayPal order has not been approved yet. Approve it in the PayPal Sandbox window, then capture again.";
      case "INSTRUMENT_DECLINED" ->
          "PayPal declined the funding source. Approve the order again with a different Sandbox funding source, then capture.";
      case "TRANSACTION_REFUSED" ->
          "PayPal refused this payment. In the Sandbox checkout, approve the order again with a different funding source (for example a test card instead of the PayPal balance).";
      case "INVALID_ACCOUNT_STATUS" ->
          "The PayPal Sandbox account is locked or inactive. Resolve its status in the Sandbox accounts page, then try again.";
      default -> "PayPal rejected the request (HTTP " + status + ", " + issue + ")";
    };
  }

  /**
   * First PayPal error issue code from the response body (for example
   * {@code ORDER_NOT_APPROVED}). Only this stable, non-sensitive code is read —
   * never tokens, headers, or the rest of the body.
   */
  private static String issueCode(ClientHttpResponse response) {
    try (InputStream body = response.getBody()) {
      if (body == null) {
        return null;
      }
      JsonNode root = JSON.readTree(StreamUtils.copyToString(body, StandardCharsets.UTF_8));
      JsonNode details = root.path("details");
      if (details.isArray() && !details.isEmpty()) {
        JsonNode issue = details.get(0).path("issue");
        if (!issue.isMissingNode() && !issue.asText().isBlank()) {
          return issue.asText();
        }
      }
      JsonNode name = root.path("name");
      if (!name.isMissingNode() && !name.asText().isBlank()) {
        return name.asText();
      }
    } catch (Exception ignored) {
      // Never fail while building an error message.
    }
    return null;
  }

  private static RestClient buildRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }
}
