package com.guardpay.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formats HTTP payloads for the development console.
 *
 * <p>Bodies and headers are logged so backend calls are traceable, but
 * credentials never reach the terminal: header names and JSON/form keys that
 * look sensitive (authorization, tokens, secrets, cookies, signatures,
 * certificates) are replaced with {@value #MASK}. User-authored content and
 * destination PII (message/invoice text, URLs, names, emails) are masked too so
 * the privacy claim holds. JSON is pretty-printed and every payload is bounded
 * by {@code maxChars}.
 */
final class HttpLogSanitizer {

  static final String MASK = "***";

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final Set<String> SENSITIVE_NAME_PARTS =
      Set.of(
          "authorization",
          "auth",
          "cookie",
          "token",
          "secret",
          "password",
          "passwd",
          "api-key",
          "apikey",
          "api_key",
          "signature",
          "transmission_sig",
          "transmission-sig",
          "cert",
          "credential",
          "cvv",
          "email");

  /** Exact names that would otherwise be too generic to mask by substring. */
  private static final Set<String> SENSITIVE_EXACT_NAMES =
      Set.of(
          "verificationcode",
          "challengecode",
          "otp",
          "verification_code",
          "text",
          "url",
          "message",
          "description",
          "contenttext",
          "contenturl",
          "recipientname",
          "merchantname");

  private HttpLogSanitizer() {}

  static boolean isSensitiveName(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    String normalized = name.toLowerCase(Locale.ROOT);
    return SENSITIVE_EXACT_NAMES.contains(normalized)
        || SENSITIVE_NAME_PARTS.stream().anyMatch(normalized::contains);
  }

  static String maskHeaderValue(String name, String value) {
    return isSensitiveName(name) ? MASK : value;
  }

  /** A generic "code" field is only secret when its value looks like an OTP. */
  private static boolean isOtpCode(String name, JsonNode value) {
    if (name == null || !"code".equals(name.toLowerCase(Locale.ROOT)) || !value.isTextual()) {
      return false;
    }
    return value.asText().matches("\\d{4,8}");
  }

  private static boolean isOtpCodePair(String name, String value) {
    return "code".equalsIgnoreCase(name) && value != null && value.matches("\\d{4,8}");
  }

  static boolean isTextualContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return true;
    }
    String type = contentType.toLowerCase(Locale.ROOT);
    return type.startsWith("application/json")
        || type.contains("+json")
        || type.startsWith("text/")
        || type.contains("xml")
        || type.startsWith("application/x-www-form-urlencoded")
        || type.contains("javascript");
  }

  /** Sanitizes a raw query string (secrets are masked, everything else kept). */
  static String query(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return "";
    }
    return maskKeyValuePairs(rawQuery);
  }

  /**
   * Returns a readable, sanitized body. JSON is pretty-printed, form data is
   * key/value masked, and other text is logged verbatim. Never throws.
   */
  static String body(String body, String contentType, int maxChars) {
    if (body == null || body.isEmpty()) {
      return "<empty>";
    }
    String rendered;
    if (looksLikeJson(body) || (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json"))) {
      rendered = prettyJson(body);
    } else if (contentType != null
        && contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
      rendered = maskKeyValuePairs(body);
    } else {
      rendered = body.replace("\r\n", "\n").replace('\r', '\n');
    }
    return truncate(rendered, maxChars);
  }

  static String indent(String text, String indentation) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    return indentation + text.replace("\n", "\n" + indentation);
  }

  private static boolean looksLikeJson(String body) {
    String trimmed = body.trim();
    return trimmed.startsWith("{") || trimmed.startsWith("[");
  }

  private static String prettyJson(String body) {
    try {
      JsonNode root = JSON.readTree(body);
      if (root == null) {
        return body;
      }
      return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(mask(root));
    } catch (Exception unreadable) {
      return body;
    }
  }

  private static JsonNode mask(JsonNode node) {
    if (node.isObject()) {
      ObjectNode masked = JSON.createObjectNode();
      node.fields()
          .forEachRemaining(
              field ->
                  masked.set(
                      field.getKey(),
                      isSensitiveName(field.getKey()) || isOtpCode(field.getKey(), field.getValue())
                          ? TextNode.valueOf(MASK)
                          : mask(field.getValue())));
      return masked;
    }
    if (node.isArray()) {
      ArrayNode masked = JSON.createArrayNode();
      node.forEach(item -> masked.add(mask(item)));
      return masked;
    }
    return node;
  }

  private static String maskKeyValuePairs(String raw) {
    return Arrays.stream(raw.split("&"))
        .map(
            pair -> {
              int separator = pair.indexOf('=');
              if (separator <= 0) {
                return pair;
              }
              String name = pair.substring(0, separator);
              String value = pair.substring(separator + 1);
              return isSensitiveName(name) || isOtpCodePair(name, value)
                  ? name + "=" + MASK
                  : pair;
            })
        .collect(Collectors.joining("&"));
  }

  private static String truncate(String text, int maxChars) {
    if (maxChars <= 0 || text.length() <= maxChars) {
      return text;
    }
    int omitted = text.length() - maxChars;
    return text.substring(0, maxChars) + "\n... [" + omitted + " more characters]";
  }
}
