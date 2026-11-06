package com.guardpay.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the opaque session cookie into a request-scoped user. This is a
 * deliberately small authentication boundary (no Spring Security filter chain)
 * because GuardPay only needs login-gated JSON APIs plus an SSE stream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(
    name = "guardpay.auth.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthFilter extends OncePerRequestFilter {

  private static final List<String> PUBLIC_EXACT =
      List.of(
          "/api/auth/me",
          "/api/auth/login",
          "/api/auth/register",
          "/api/auth/logout",
          "/api/auth/csrf",
          "/api/auth/password/forgot",
          "/api/auth/password/reset",
          "/api/health",
          "/error",
          "/favicon.ico");

  private static final List<String> PUBLIC_PREFIXES =
      List.of("/api/paypal/webhooks/", "/api/device-signals/", "/api/dev/");

  private final AuthSessionRepository sessions;

  public AuthFilter(AuthSessionRepository sessions) {
    this.sessions = sessions;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }

    resolveSession(request);
    try {
      if (!isPublic(request.getRequestURI()) && AuthContext.currentUser() == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
        return;
      }
      chain.doFilter(request, response);
    } finally {
      AuthContext.clear();
    }
  }

  private void resolveSession(HttpServletRequest request) {
    String token = sessionToken(request);
    if (token == null) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    sessions
        .findActiveByTokenHash(AuthService.hash(token), now)
        .ifPresent(
            session -> {
              sessions.touch(session.getId(), now, now.plus(AuthService.SESSION_IDLE_TTL));
              AuthContext.set(session);
            });
  }

  private static String sessionToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (AuthService.COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue() == null || cookie.getValue().isBlank() ? null : cookie.getValue();
      }
    }
    return null;
  }

  private static boolean isPublic(String path) {
    if (path == null) {
      return true;
    }
    return PUBLIC_EXACT.contains(path) || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
  }
}
