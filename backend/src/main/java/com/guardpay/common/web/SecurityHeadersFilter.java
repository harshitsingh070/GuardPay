package com.guardpay.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Baseline response hardening plus optional HTTPS enforcement behind a proxy. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

  private final boolean requireHttps;

  public SecurityHeadersFilter(
      @Value("${guardpay.auth.require-https:false}") boolean requireHttps) {
    this.requireHttps = requireHttps;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

    boolean secure = isSecure(request);
    if (secure) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
    if (requireHttps && !secure) {
      response.setStatus(426);
      response.setContentType("application/json");
      response.getWriter().write("{\"status\":426,\"error\":\"Upgrade Required\",\"message\":\"HTTPS is required\"}");
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean isSecure(HttpServletRequest request) {
    if (request.isSecure()) {
      return true;
    }
    String forwarded = request.getHeader("X-Forwarded-Proto");
    return forwarded != null && "https".equalsIgnoreCase(forwarded.trim());
  }
}
