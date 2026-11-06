package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class CsrfFilterTest {

  private static final String SESSION_TOKEN = "opaque-session-token";

  @Mock private FilterChain chain;

  private CsrfTokenService tokens;
  private CsrfFilter filter;

  @BeforeEach
  void setUp() {
    tokens = new CsrfTokenService("test-csrf-secret");
    filter = new CsrfFilter(tokens);
  }

  @Test
  void rejectsUnsafeRequestWithoutCsrfHeader() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/payments/guarded", SESSION_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(403, response.getStatus());
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void acceptsUnsafeRequestWithSessionBoundToken() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/payments/guarded", SESSION_TOKEN);
    request.addHeader(
        CsrfTokenService.HEADER,
        tokens.issue(CsrfTokenService.scopeFor(SESSION_TOKEN)));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(200, response.getStatus());
    verify(chain).doFilter(request, response);
  }

  @Test
  void rejectsTokenBoundToAnotherSession() throws Exception {
    MockHttpServletRequest request =
        request("POST", "/api/auth/logout", SESSION_TOKEN);
    request.addHeader(
        CsrfTokenService.HEADER,
        tokens.issue(CsrfTokenService.scopeFor("another-session")));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(403, response.getStatus());
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void safeMethodsDoNotRequireAToken() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/transactions", SESSION_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  @Test
  void webhooksAndDeviceSignalsAreExempt() throws Exception {
    MockHttpServletRequest webhook =
        request("POST", "/api/paypal/webhooks/signature", null);
    MockHttpServletResponse webhookResponse = new MockHttpServletResponse();
    filter.doFilter(webhook, webhookResponse, chain);

    MockHttpServletRequest device =
        request("POST", "/api/device-signals/call-state", null);
    MockHttpServletResponse deviceResponse = new MockHttpServletResponse();
    filter.doFilter(device, deviceResponse, chain);

    verify(chain).doFilter(webhook, webhookResponse);
    verify(chain).doFilter(device, deviceResponse);
  }

  @Test
  void anonymousMutationNeedsTheAnonymousToken() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/auth/login", null);
    request.addHeader(
        CsrfTokenService.HEADER, tokens.issue(CsrfTokenService.ANONYMOUS_SCOPE));
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }

  private static MockHttpServletRequest request(String method, String path, String cookie) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    if (cookie != null) {
      request.setCookies(new Cookie(AuthService.COOKIE_NAME, cookie));
    }
    return request;
  }
}
