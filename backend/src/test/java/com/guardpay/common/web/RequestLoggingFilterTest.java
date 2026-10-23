package com.guardpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

  private final RequestLoggingFilter filter = new RequestLoggingFilter();
  private final Logger filterLogger =
      (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> logCapture = new ListAppender<>();

  @BeforeEach
  void attachLogCapture() {
    logCapture.start();
    filterLogger.addAppender(logCapture);
  }

  @AfterEach
  void clearMdc() {
    filterLogger.detachAppender(logCapture);
    logCapture.stop();
    MDC.clear();
  }

  @Test
  void addsARequestIdHeaderAndClearsTheMdc() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/guarded");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) ->
            ((HttpServletResponse) servletResponse).setStatus(201));

    assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    assertThat(request.getAttribute(RequestLoggingFilter.REQUEST_ID_ATTRIBUTE))
        .isEqualTo(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
    assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void keepsTheResponseStatusProducedDownstream() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/device-signals/call-state");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) ->
            ((HttpServletResponse) servletResponse).setStatus(401));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void propagatesDownstreamFailuresAndStillClearsTheMdc() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (servletRequest, servletResponse) -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void logsSanitizedBodiesAndKeepsRequestAndResponseReadable() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payments/guarded");
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    request.setContentType("application/json");
    String requestBody = "{\"amount\":45,\"access_token\":\"super-secret\"}";
    request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));
    request.addHeader("Authorization", "Bearer super-secret");
    request.addHeader("X-GuardPay-Device-Token", "device-secret");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          String forwarded =
              new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
          assertThat(forwarded).isEqualTo(requestBody);
          ((HttpServletResponse) servletResponse).setContentType("application/json");
          servletResponse
              .getWriter()
              .write("{\"status\":\"VERIFY\",\"challengeCode\":\"123456\"}");
        });

    String logs =
        logCapture.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));

    assertThat(logs).contains("request body:").contains("\"amount\" : 45");
    assertThat(logs).contains("\"access_token\" : \"***\"");
    assertThat(logs).contains("response body:").contains("\"status\" : \"VERIFY\"");
    assertThat(logs).contains("\"challengeCode\" : \"***\"");
    assertThat(logs).doesNotContain("super-secret");
    assertThat(logs).doesNotContain("device-secret");
    assertThat(logs).doesNotContain("123456");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"status\":\"VERIFY\",\"challengeCode\":\"123456\"}");
  }
}
