package com.guardpay.paypal.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import com.guardpay.paypal.exception.PayPalException;
import com.guardpay.paypal.exception.PayPalNotConfiguredException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * OAuth flow against a mock PayPal server. No live credentials, no network.
 */
class PayPalOAuthServiceTest {

  private static final String BASE_URL = "https://api-m.sandbox.paypal.com";
  private static final Instant T0 = Instant.parse("2026-10-06T10:00:00Z");

  private PayPalProperties properties;
  private RestClient.Builder builder;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    properties = new PayPalProperties();
    properties.setBaseUrl(BASE_URL);
    properties.setClientId("test-client-id");
    properties.setClientSecret("test-client-secret");
    builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
  }

  private PayPalOAuthService service(Clock clock) {
    return new PayPalOAuthService(properties, builder.build(), clock);
  }

  private void expectTokenRequest(String tokenJson) {
    server
        .expect(requestTo(BASE_URL + "/v1/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Basic ")))
        .andExpect(content().string("grant_type=client_credentials"))
        .andRespond(withSuccess(tokenJson, MediaType.APPLICATION_JSON));
  }

  private static String tokenJson(String token, long expiresIn) {
    return "{\"access_token\":\""
        + token
        + "\",\"token_type\":\"Bearer\",\"expires_in\":"
        + expiresIn
        + "}";
  }

  @Test
  void validCachedTokenIsReused() {
    expectTokenRequest(tokenJson("tok-1", 3600));
    PayPalOAuthService oauth = service(Clock.fixed(T0, ZoneOffset.UTC));

    assertEquals("tok-1", oauth.getAccessToken());
    assertEquals("tok-1", oauth.getAccessToken());

    server.verify();
  }

  @Test
  void nearExpiryTokenCausesRefresh() {
    // Expectations must all be declared before any request is made.
    expectTokenRequest(tokenJson("tok-1", 100)); // usable for 100 - 60 = 40s
    expectTokenRequest(tokenJson("tok-2", 3600));
    PayPalOAuthService oauth = service(Clock.fixed(T0, ZoneOffset.UTC));
    assertEquals("tok-1", oauth.getAccessToken());

    oauth.setClock(Clock.fixed(T0.plusSeconds(41), ZoneOffset.UTC));
    assertEquals("tok-2", oauth.getAccessToken());

    server.verify();
  }

  @Test
  void malformedTokenResponseThrowsSafeError() {
    expectTokenRequest("{\"token_type\":\"Bearer\",\"expires_in\":3600}");
    PayPalOAuthService oauth = service(Clock.fixed(T0, ZoneOffset.UTC));

    PayPalException failure = assertThrows(PayPalException.class, oauth::getAccessToken);

    assertFalse(failure.getMessage().contains("test-client-secret"));
    assertFalse(failure.getMessage().contains("test-client-id"));
  }

  @Test
  void rejectedCredentialsThrowAuthErrorWithoutLeak() {
    server
        .expect(requestTo(BASE_URL + "/v1/oauth2/token"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withUnauthorizedRequest());
    PayPalOAuthService oauth = service(Clock.fixed(T0, ZoneOffset.UTC));

    PayPalAuthenticationException failure =
        assertThrows(PayPalAuthenticationException.class, oauth::getAccessToken);

    assertTrue(failure.getMessage().contains("401"));
    assertFalse(failure.getMessage().contains("test-client-secret"));
    assertFalse(failure.getMessage().contains("test-client-id"));
  }

  @Test
  void missingCredentialsFailBeforeAnyRequest() {
    properties.setClientId("");
    properties.setClientSecret("");
    PayPalOAuthService oauth = service(Clock.fixed(T0, ZoneOffset.UTC));

    PayPalNotConfiguredException failure =
        assertThrows(PayPalNotConfiguredException.class, oauth::getAccessToken);

    assertTrue(failure.getMessage().contains("PAYPAL_CLIENT_SECRET"));
    server.verify(); // proves no HTTP attempt was made
  }
}
