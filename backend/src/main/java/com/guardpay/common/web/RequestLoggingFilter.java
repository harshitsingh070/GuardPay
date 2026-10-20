package com.guardpay.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Logs every inbound HTTP call with its headers and body, then the status,
 * duration, and response body. Runs before Spring Security so rejected calls
 * (for example a wrong device token) are visible too.
 *
 * <p>Credentials are masked by {@link HttpLogSanitizer}: the device token,
 * Authorization/Cookie headers, and JSON keys containing token/secret/
 * password/signature are replaced with {@code ***}. Bodies are bounded by
 * {@code guardpay.logging.http.max-body-chars} (default 8000) so a large
 * transaction list cannot flood the terminal.
 *
 * <p>Set {@code guardpay.logging.http.include-bodies} or
 * {@code guardpay.logging.http.include-headers} to {@code false} to reduce
 * console noise. Both default to true for end-to-end tracing in development.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  static final String REQUEST_ID_ATTRIBUTE = "guardpay.requestId";
  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_MDC_KEY = "requestId";

  @Value("${guardpay.logging.http.include-headers:true}")
  private boolean includeHeaders = true;

  @Value("${guardpay.logging.http.include-bodies:true}")
  private boolean includeBodies = true;

  @Value("${guardpay.logging.http.max-body-chars:8000}")
  private int maxBodyChars = 8_000;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    String method = request.getMethod();
    String path = request.getRequestURI();
    String client = request.getRemoteAddr();

    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);

    HttpServletRequest requestToUse = request;
    String requestBody = null;
    if (includeBodies
        && hasPotentialBody(request)
        && HttpLogSanitizer.isTextualContentType(request.getContentType())) {
      CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
      requestToUse = cached;
      requestBody = cached.bodyAsString();
    }

    logRequest(request, method, path, requestId, client, requestBody);

    ContentCachingResponseWrapper responseToUse = new ContentCachingResponseWrapper(response);
    boolean failed = false;
    try {
      filterChain.doFilter(requestToUse, responseToUse);
    } catch (ServletException | IOException | RuntimeException ex) {
      failed = true;
      log.error(
          "HTTP {} {} failed after {} ms (client={}, requestId={})",
          method,
          path,
          elapsedMillis(startedAt),
          client,
          requestId,
          ex);
      throw ex;
    } finally {
      try {
        if (!failed) {
          logResponse(method, path, responseToUse, elapsedMillis(startedAt), client, requestId);
        }
      } finally {
        try {
          responseToUse.copyBodyToResponse();
        } catch (IOException copyFailure) {
          log.warn(
              "Could not copy the response body back to the client (requestId={}): {}",
              requestId,
              copyFailure.getMessage());
        } finally {
          MDC.remove(REQUEST_ID_MDC_KEY);
        }
      }
    }
  }

  private void logRequest(
      HttpServletRequest request,
      String method,
      String path,
      String requestId,
      String client,
      String requestBody) {
    StringBuilder message = new StringBuilder(512);
    message.append("--> ").append(method).append(' ').append(path);
    String query = HttpLogSanitizer.query(request.getQueryString());
    if (!query.isEmpty()) {
      message.append('?').append(query);
    }
    message
        .append("\n   requestId=")
        .append(requestId)
        .append(" client=")
        .append(client)
        .append(" content-type=")
        .append(orDash(request.getContentType()));

    if (includeHeaders) {
      message.append("\n   request headers:");
      for (String name : sortedHeaderNames(Collections.list(request.getHeaderNames()))) {
        String value = String.join(", ", Collections.list(request.getHeaders(name)));
        message
            .append("\n      ")
            .append(name)
            .append(": ")
            .append(HttpLogSanitizer.maskHeaderValue(name, value));
      }
    }

    if (requestBody != null) {
      message
          .append("\n   request body:\n")
          .append(
              HttpLogSanitizer.indent(
                  HttpLogSanitizer.body(requestBody, request.getContentType(), maxBodyChars),
                  "      "));
    }
    log.info(message.toString());
  }

  private void logResponse(
      String method,
      String path,
      ContentCachingResponseWrapper response,
      long durationMs,
      String client,
      String requestId) {
    int status = response.getStatus();
    StringBuilder message = new StringBuilder(512);
    message
        .append("<-- ")
        .append(method)
        .append(' ')
        .append(path)
        .append(" -> ")
        .append(status)
        .append(" in ")
        .append(durationMs)
        .append(" ms (client=")
        .append(client)
        .append(", requestId=")
        .append(requestId)
        .append(')');

    if (includeHeaders) {
      message.append("\n   response headers:");
      for (String name : sortedHeaderNames(response.getHeaderNames())) {
        String value = String.join(", ", response.getHeaders(name));
        message
            .append("\n      ")
            .append(name)
            .append(": ")
            .append(HttpLogSanitizer.maskHeaderValue(name, value));
      }
    }

    if (includeBodies) {
      message.append("\n   response body:").append(describeBody(response));
    }

    if (status >= 500) {
      log.error(message.toString());
    } else if (status >= 400) {
      log.warn(message.toString());
    } else {
      log.info(message.toString());
    }
  }

  private String describeBody(ContentCachingResponseWrapper response) {
    byte[] body = response.getContentAsByteArray();
    if (body.length == 0) {
      return " <empty>";
    }
    String contentType = response.getContentType();
    if (!HttpLogSanitizer.isTextualContentType(contentType)) {
      return " <" + body.length + " bytes of " + orDash(contentType) + ">";
    }
    String text = new String(body, charsetOf(response.getCharacterEncoding()));
    String formatted = HttpLogSanitizer.body(text, contentType, maxBodyChars);
    return "\n" + HttpLogSanitizer.indent(formatted, "      ");
  }

  private static boolean hasPotentialBody(HttpServletRequest request) {
    String method = request.getMethod();
    if ("GET".equalsIgnoreCase(method)
        || "HEAD".equalsIgnoreCase(method)
        || "OPTIONS".equalsIgnoreCase(method)) {
      return false;
    }
    return request.getContentLengthLong() != 0;
  }

  private static List<String> sortedHeaderNames(Collection<String> names) {
    TreeSet<String> unique = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    unique.addAll(names);
    return List.copyOf(unique);
  }

  private static Charset charsetOf(String encoding) {
    try {
      return encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    } catch (Exception unsupported) {
      return StandardCharsets.UTF_8;
    }
  }

  private static String orDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
