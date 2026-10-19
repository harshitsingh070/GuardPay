package com.guardpay.paypal.auth;

import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.dto.PayPalAccessTokenResponse;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.paypal.exception.PayPalNotConfiguredException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * PayPal OAuth 2.0 client-credentials flow against {@code POST /v1/oauth2/token}.
 *
 * <p>Tokens are cached in memory and refreshed ~60 seconds before expiry.
 * The token itself is never logged and never leaves the backend.
 */
@Service
public class PayPalOAuthService {

  private static final Logger log = LoggerFactory.getLogger(PayPalOAuthService.class);

  /** Refresh buffer before {@code expires_in} elapses. */
  static final Duration EXPIRY_BUFFER = Duration.ofSeconds(60);

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

  private final PayPalProperties properties;
  private final RestClient restClient;
  private Clock clock;

  private String cachedToken;
  private Instant tokenExpiresAt;

  @Autowired
  public PayPalOAuthService(PayPalProperties properties) {
    this(properties, buildRestClient(properties.getBaseUrl()), Clock.systemUTC());
  }

  /** Test hook: inject mock-server client and controllable clock. */
  PayPalOAuthService(PayPalProperties properties, RestClient restClient, Clock clock) {
    this.properties = properties;
    this.restClient = restClient;
    this.clock = clock;
  }

  /** Test hook: travel in time for expiry tests. */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  /**
   * Returns a valid bearer token, reusing the cached one when it is still
   * fresh. Synchronized so concurrent callers share one refresh.
   *
   * @throws PayPalNotConfiguredException when PayPal credentials are not configured
   * @throws PayPalException on PayPal/network failures (safe messages only)
   */
  public synchronized String getAccessToken() {
    if (cachedToken != null
        && tokenExpiresAt != null
        && Instant.now(clock).isBefore(tokenExpiresAt)) {
      return cachedToken;
    }
    return refreshToken();
  }

  private String refreshToken() {
    requireConfigured();
    log.info("Requesting PayPal OAuth token ({})", properties.environmentLabel());
    try {
      PayPalAccessTokenResponse response =
          restClient
              .post()
              .uri("/v1/oauth2/token")
              .header("Authorization", basicAuthHeader())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body("grant_type=client_credentials")
              .retrieve()
              .onStatus(
                  status -> status.value() == 401 || status.value() == 403,
                  (request, paypalResponse) -> {
                    throw new PayPalAuthenticationException(
                        "PayPal rejected the API credentials (HTTP "
                            + paypalResponse.getStatusCode().value()
                            + ")");
                  })
              .onStatus(
                  status -> status.value() >= 500,
                  (request, paypalResponse) -> {
                    throw new PayPalException(
                        paypalResponse.getStatusCode().value(),
                        "PayPal is temporarily unavailable (HTTP "
                            + paypalResponse.getStatusCode().value()
                            + ")");
                  })
              .body(PayPalAccessTokenResponse.class);

      if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
        throw new PayPalException("PayPal returned an unexpected token response");
      }
      cachedToken = response.accessToken();
      long usableSeconds = Math.max(response.expiresIn() - EXPIRY_BUFFER.getSeconds(), 0);
      tokenExpiresAt = Instant.now(clock).plusSeconds(usableSeconds);
      // Deliberately no token value in logs.
      log.info("PayPal OAuth token refreshed");
      return cachedToken;
    } catch (PayPalException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new PayPalException("PayPal is unreachable (network timeout)");
    }
  }

  private void requireConfigured() {
    if (isBlank(properties.getClientId()) || isBlank(properties.getClientSecret())) {
      throw new PayPalNotConfiguredException(
          "PayPal is not configured: set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET environment variables");
    }
  }

  private String basicAuthHeader() {
    String credentials = properties.getClientId() + ":" + properties.getClientSecret();
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static RestClient buildRestClient(String baseUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
  }
}
