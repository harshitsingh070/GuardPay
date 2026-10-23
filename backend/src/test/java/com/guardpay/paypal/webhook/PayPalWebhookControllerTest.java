package com.guardpay.paypal.webhook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guardpay.common.config.CorsConfig;
import com.guardpay.paypal.webhook.dto.WebhookAckResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Webhook endpoint with a mocked service: raw-body passthrough, safe ack
 * shapes, and status mapping â€” never live PayPal.
 */
@WebMvcTest(PayPalWebhookController.class)
@Import(CorsConfig.class)
class PayPalWebhookControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private PayPalWebhookService service;

  private static final String BODY =
      "{\"id\": \"EVT-1\", \"event_type\": \"CHECKOUT.ORDER.APPROVED\","
          + " \"resource\": {\"id\": \"ORDER-1\"}}";

  @Test
  void validWebhookIsAcknowledged() throws Exception {
    when(service.handleEvent(any(HttpHeaders.class), eq(BODY)))
        .thenReturn(WebhookAckResponse.ack());

    mockMvc
        .perform(
            post("/api/paypal/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("paypal-transmission-id", "trans-1")
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.received").value(true))
        .andExpect(jsonPath("$.duplicate").doesNotExist());

    ArgumentCaptor<HttpHeaders> headers = ArgumentCaptor.forClass(HttpHeaders.class);
    verify(service).handleEvent(headers.capture(), eq(BODY));
    assertTrue(headers.getValue().containsKey("paypal-transmission-id"));
  }

  @Test
  void duplicateWebhookIsAcknowledged() throws Exception {
    when(service.handleEvent(any(HttpHeaders.class), eq(BODY)))
        .thenReturn(WebhookAckResponse.ackDuplicate());

    mockMvc
        .perform(
            post("/api/paypal/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.received").value(true))
        .andExpect(jsonPath("$.duplicate").value(true));
  }

  @Test
  void invalidSignatureMapsToUnauthorized() throws Exception {
    when(service.handleEvent(any(HttpHeaders.class), eq(BODY)))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature"));

    mockMvc
        .perform(
            post("/api/paypal/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnauthorized());
  }
}
