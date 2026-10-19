package com.guardpay.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.guardpay.common.dto.ApiError;
import com.guardpay.paypal.exception.PayPalNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Proves missing PayPal configuration surfaces as a safe 502 (what the UI
 * shows as "PayPal service unavailable") instead of a generic 500.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void payPalNotConfiguredMapsToSafeBadGateway() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/paypal/orders");

    ResponseEntity<ApiError> response =
        handler.handlePayPal(
            new PayPalNotConfiguredException(
                "PayPal is not configured: set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET"
                    + " environment variables"),
            request);

    assertEquals(502, response.getStatusCode().value());
    assertEquals("PayPal service unavailable", response.getBody().message());
    assertFalse(response.getBody().message().contains("Basic"));
    assertFalse(response.getBody().message().contains("Bearer"));
  }
}
