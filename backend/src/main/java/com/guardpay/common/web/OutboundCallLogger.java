package com.guardpay.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs outbound HTTP calls (PayPal, PayPal OAuth, AI service) with their
 * request/response bodies so a payment can be followed end-to-end in the
 * terminal. Credentials and tokens are masked by {@link HttpLogSanitizer}.
 *
 * <p>Usage:
 *
 * <pre>
 *   long startedAt = OutboundCallLogger.request("PAYPAL", "POST", url, body);
 *   try {
 *     ...
 *     OutboundCallLogger.success("PAYPAL", "POST", url, startedAt, response);
 *   } catch (RuntimeException failure) {
 *     OutboundCallLogger.failure("PAYPAL", "POST", url, startedAt, -1, failure);
 *     throw failure;
 *   }
 * </pre>
 */
public final class OutboundCallLogger {

  private static final Logger log = LoggerFactory.getLogger(OutboundCallLogger.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_BODY_CHARS = 8_000;

  private OutboundCallLogger() {}

  /** Logs the outbound request and returns its start timestamp. */
  public static long request(String service, String method, String url, Object body) {
    log.info(
        "OUTBOUND {} {} {}\n{}",
        service,
        method,
        url,
        formatBody("request", body));
    return System.nanoTime();
  }

  /** Logs a completed outbound call with its response body. */
  public static void success(
      String service, String method, String url, long startedAt, Object response) {
    log.info(
        "OUTBOUND {} {} {} -> OK in {} ms\n{}",
        service,
        method,
        url,
        elapsedMillis(startedAt),
        formatBody("response", response));
  }

  /** Logs a failed outbound call; status {@code -1} means no HTTP response arrived. */
  public static void failure(
      String service, String method, String url, long startedAt, int status, Throwable error) {
    String outcome = status > 0 ? "HTTP " + status : "NO RESPONSE";
    String detail =
        error == null
            ? ""
            : " (" + describe(error) + (error.getMessage() == null ? "" : ": " + error.getMessage()) + ")";
    long duration = elapsedMillis(startedAt);
    if (status >= 400 && status < 500) {
      log.warn("OUTBOUND {} {} {} -> {} in {} ms{}", service, method, url, outcome, duration, detail);
    } else {
      log.error("OUTBOUND {} {} {} -> {} in {} ms{}", service, method, url, outcome, duration, detail);
    }
  }

  private static String formatBody(String label, Object body) {
    String raw;
    if (body == null) {
      raw = "<none>";
    } else if (body instanceof CharSequence text) {
      raw = text.toString();
    } else {
      try {
        raw = JSON.writeValueAsString(body);
      } catch (Exception unserializable) {
        raw = String.valueOf(body);
      }
    }
    String formatted = HttpLogSanitizer.body(raw, "application/json", MAX_BODY_CHARS);
    return "   " + label + " body:\n" + HttpLogSanitizer.indent(formatted, "      ");
  }

  private static String describe(Throwable error) {
    return error == null ? "error" : error.getClass().getSimpleName();
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }
}
