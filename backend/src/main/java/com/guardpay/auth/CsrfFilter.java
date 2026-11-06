package com.guardpay.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Double-submit-style CSRF protection for cookie-authenticated mutations.
 * Webhooks and device-signal intake use their own signatures/credentials and
 * are exempt; every other unsafe request must echo the session-bound token.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@ConditionalOnExpression(
    "${guardpay.auth.enabled:true} and ${guardpay.auth.csrf-enabled:true}")
public class CsrfFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  private final CsrfTokenService tokens;

  public CsrfFilter(CsrfTokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SAFE_METHODS.contains(request.getMethod().toUpperCase())
        || isExempt(request.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }

    String supplied = request.getHeader(CsrfTokenService.HEADER);
    String sessionToken = sessionToken(request);
    if (!tokens.isValid(supplied, CsrfTokenService.scopeFor(sessionToken))) {
      response.setStatus(HttpServletResponse.SC_FORBIDDEN);
      response.setContentType("application/json");
      response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Invalid CSRF token\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isExempt(String path) {
    return path != null
        && (path.startsWith("/api/paypal/webhooks/") || path.startsWith("/api/device-signals/"));
  }

  private static String sessionToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (AuthService.COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
