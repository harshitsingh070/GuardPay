package com.guardpay.paypal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.config.PayPalProperties;
import com.guardpay.paypal.exception.PayPalAuthenticationException;
import com.guardpay.paypal.exception.PayPalNotConfiguredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Health mapping with a mocked OAuth source. Statuses only — no secrets involved. */
@ExtendWith(MockitoExtension.class)
class PayPalServiceTest {

  @Mock private PayPalOAuthService oauthService;

  private PayPalService service;

  @BeforeEach
  void setUp() {
    PayPalProperties properties = new PayPalProperties();
    service = new PayPalService(oauthService, properties);
  }

  @Test
  void validTokenReportsUpSandbox() {
    when(oauthService.getAccessToken()).thenReturn("tok");

    PayPalConnectionStatus status = service.checkConnection();

    assertEquals("UP", status.status());
    assertEquals("SANDBOX", status.environment());
  }

  @Test
  void rejectedCredentialsReportDown() {
    when(oauthService.getAccessToken())
        .thenThrow(new PayPalAuthenticationException("PayPal rejected the API credentials (HTTP 401)"));

    PayPalConnectionStatus status = service.checkConnection();

    assertEquals("DOWN", status.status());
    assertEquals("SANDBOX", status.environment());
  }

  @Test
  void missingConfigurationReportsDown() {
    when(oauthService.getAccessToken())
        .thenThrow(new PayPalNotConfiguredException("PayPal is not configured"));

    assertEquals("DOWN", service.checkConnection().status());
  }
}
