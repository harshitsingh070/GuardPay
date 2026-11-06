package com.guardpay.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

  @Mock private FilterChain chain;

  @Test
  void addsBaselineHeaders() throws Exception {
    SecurityHeadersFilter filter = new SecurityHeadersFilter(false);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    assertEquals("DENY", response.getHeader("X-Frame-Options"));
    assertEquals("no-referrer", response.getHeader("Referrer-Policy"));
    assertEquals(
        "camera=(), microphone=(), geolocation=()", response.getHeader("Permissions-Policy"));
    assertNull(response.getHeader("Strict-Transport-Security"));
    verify(chain).doFilter(request, response);
  }

  @Test
  void addsHstsForForwardedHttps() throws Exception {
    SecurityHeadersFilter filter = new SecurityHeadersFilter(false);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    request.addHeader("X-Forwarded-Proto", "https");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(
        "max-age=31536000; includeSubDomains",
        response.getHeader("Strict-Transport-Security"));
    verify(chain).doFilter(request, response);
  }

  @Test
  void rejectsPlainHttpWhenHttpsIsRequired() throws Exception {
    SecurityHeadersFilter filter = new SecurityHeadersFilter(true);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    assertEquals(426, response.getStatus());
    verify(chain, never()).doFilter(request, response);
  }
}
