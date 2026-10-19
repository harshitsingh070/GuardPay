package com.guardpay.paypal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Authenticated-call behavior with a mocked token source. No network. */
@ExtendWith(MockitoExtension.class)
class PayPalClientTest {

  private static final String BASE_URL = "https://api-m.sandbox.paypal.com";

  @Mock private PayPalOAuthService oauthService;

  private RestClient.Builder builder;
  private MockRestServiceServer server;
  private PayPalClient client;

  @BeforeEach
  void setUp() {
    builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new PayPalClient(oauthService, builder.build());
  }

  @Test
  void getSendsBearerTokenAndParsesBody() {
    when(oauthService.getAccessToken()).thenReturn("test-token");
    server
        .expect(requestTo(BASE_URL + "/v1/some-resource"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

    assertEquals("ok", client.get("/v1/some-resource", String.class));
    server.verify();
  }

  @Test
  void unauthorizedBecomesSafeAuthError() {
    when(oauthService.getAccessToken()).thenReturn("test-token");
    server
        .expect(requestTo(BASE_URL + "/v1/some-resource"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withUnauthorizedRequest());

    PayPalAuthenticationException failure =
        assertThrows(PayPalAuthenticationException.class, () -> client.get("/v1/some-resource", String.class));

    assertFalse(failure.getMessage().contains("test-token"));
  }
}
