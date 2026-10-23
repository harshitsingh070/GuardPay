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
  void masksVerificationCodesAndChallengeCodes() {
    String body =
        HttpLogSanitizer.body(
            "{\"code\":\"123456\",\"challengeCode\":\"654321\",\"statusCode\":200,"
                + "\"findings\":[{\"code\":\"ACTIVE_CALL\"}]}",
            "application/json",
            8_000);

    assertThat(body).contains("\"code\" : \"***\"");
    assertThat(body).contains("\"challengeCode\" : \"***\"");
    assertThat(body).contains("\"statusCode\" : 200");
    assertThat(body).contains("\"code\" : \"ACTIVE_CALL\"");
    assertThat(body).doesNotContain("123456").doesNotContain("654321");
  }

  @Test
  void masksUserContentAndDestinationPii() {
    String body =
        HttpLogSanitizer.body(
            "{\"amount\":45,\"description\":\"gift\",\"contentText\":\"call support now\","
                + "\"contentUrl\":\"https://scam.test/pay\",\"recipientName\":\"Sam\","
                + "\"recipientEmail\":\"sam@example.com\",\"merchantName\":\"Shop\","
                + "\"text\":\"invoice\",\"url\":\"https://shop.test\"}",
            "application/json",
            8_000);

    assertThat(body).contains("\"amount\" : 45");
    assertThat(body).contains("\"recipientName\" : \"***\"");
    assertThat(body).contains("\"recipientEmail\" : \"***\"");
    assertThat(body)
        .doesNotContain("call support now")
        .doesNotContain("scam.test")
        .doesNotContain("sam@example.com")
        .doesNotContain("\"Sam\"")
        .doesNotContain("\"Shop\"");
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
