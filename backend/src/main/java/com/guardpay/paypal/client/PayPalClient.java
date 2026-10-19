package com.guardpay.paypal.client;

import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import com.guardpay.paypal.exception.PayPalException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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

  private final PayPalOAuthService oauthService;
  private final RestClient restClient;

  @Autowired
  public PayPalClient(PayPalOAuthService oauthService, PayPalProperties properties) {
    this(oauthService, buildRestClient(properties.getBaseUrl()));
  }

  /** Test hook: inject mock-server client. */
  PayPalClient(PayPalOAuthService oauthService, RestClient restClient) {
    this.oauthService = oauthService;
    this.restClient = restClient;
  }

  /** Authenticated GET returning a parsed body. Safe errors only. */
  public <T> T get(String path, Class<T> responseType) {
    try {
      return restClient
          .get()
          .uri(path)
          .header("Authorization", "Bearer " + oauthService.getAccessToken())
          .retrieve()
          .onStatus(
              status -> status.value() == 401 || status.value() == 403,
              (request, response) -> {
                throw new PayPalAuthenticationException(authErrorMessage(response.getStatusCode().value()));
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
                    response.getStatusCode().value(),
                    rejectedMessage(response.getStatusCode().value()));
              })
          .body(responseType);
    } catch (PayPalException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new PayPalException("PayPal is unreachable (network timeout)");
    }
  }

  /** Authenticated POST with a JSON body returning a parsed body. Safe errors only. */
  public <T> T post(String path, Object body, Class<T> responseType) {
    try {
      return restClient
          .post()
          .uri(path)
          .header("Authorization", "Bearer " + oauthService.getAccessToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .onStatus(
              status -> status.value() == 401 || status.value() == 403,
              (request, response) -> {
                throw new PayPalAuthenticationException(authErrorMessage(response.getStatusCode().value()));
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
                    response.getStatusCode().value(),
                    rejectedMessage(response.getStatusCode().value()));
              })
          .body(responseType);
    } catch (PayPalException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new PayPalException("PayPal is unreachable (network timeout)");
    }
  }

  private static String authErrorMessage(int status) {
    return "PayPal rejected the API credentials (HTTP " + status + ")";
  }

  private static String unavailableMessage(int status) {
    return "PayPal is temporarily unavailable (HTTP " + status + ")";
  }

  private static String rejectedMessage(int status) {
    return "PayPal rejected the request (HTTP " + status + ")";
  }

  private static RestClient buildRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }
}
