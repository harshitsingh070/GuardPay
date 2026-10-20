package com.guardpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HttpLogSanitizerTest {

  @Test
  void masksSensitiveQueryValuesAndKeepsTheRest() {
    assertThat(HttpLogSanitizer.query("amount=45&token=abc&recipient=Sam"))
        .isEqualTo("amount=45&token=***&recipient=Sam");
  }

  @Test
  void prettyPrintsJsonAndMasksCredentials() {
    String body =
        HttpLogSanitizer.body(
            "{\"amount\":45,\"client_secret\":\"paypal-secret\",\"access_token\":\"token\"}",
            "application/json",
            8_000);

    assertThat(body).contains("\"amount\" : 45");
    assertThat(body).contains("\"client_secret\" : \"***\"");
    assertThat(body).contains("\"access_token\" : \"***\"");
    assertThat(body).doesNotContain("paypal-secret").doesNotContain("\"token\"");
  }

  @Test
  void masksFormValues() {
    assertThat(
            HttpLogSanitizer.body(
                "grant_type=client_credentials&client_secret=paypal-secret",
                "application/x-www-form-urlencoded",
                8_000))
        .isEqualTo("grant_type=client_credentials&client_secret=***");
  }

  @Test
  void truncatesLongBodies() {
    String body = HttpLogSanitizer.body("x".repeat(50), "text/plain", 10);

    assertThat(body).startsWith("xxxxxxxxxx").contains("40 more characters");
  }

  @Test
  void identifiesBinaryContentTypes() {
    assertThat(HttpLogSanitizer.isTextualContentType("application/json")).isTrue();
    assertThat(HttpLogSanitizer.isTextualContentType("text/plain")).isTrue();
    assertThat(HttpLogSanitizer.isTextualContentType("multipart/form-data; boundary=x")).isFalse();
    assertThat(HttpLogSanitizer.isTextualContentType("application/octet-stream")).isFalse();
  }
}
