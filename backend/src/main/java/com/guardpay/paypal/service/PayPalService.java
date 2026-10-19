package com.guardpay.paypal.service;

import com.guardpay.paypal.auth.PayPalOAuthService;
import com.guardpay.paypal.config.PayPalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Application-facing PayPal layer. For this milestone it only verifies
 * connectivity/authentication — no orders, no capture.
 *
 * <p>The OAuth client-credentials grant against the Sandbox token endpoint is
 * itself the lightweight authenticated request: success proves the base URL
 * is reachable and the credentials are valid.
 */
@Service
public class PayPalService {

  private static final Logger log = LoggerFactory.getLogger(PayPalService.class);

  private final PayPalOAuthService oauthService;
  private final PayPalProperties properties;

  public PayPalService(PayPalOAuthService oauthService, PayPalProperties properties) {
    this.oauthService = oauthService;
    this.properties = properties;
  }

  /**
   * Checks PayPal connectivity. Never throws for PayPal problems — returns
   * DOWN instead — and never exposes credentials or tokens.
   */
  public PayPalConnectionStatus checkConnection() {
    try {
      oauthService.getAccessToken();
      log.info("PayPal connectivity check succeeded ({})", properties.environmentLabel());
      return new PayPalConnectionStatus("UP", properties.environmentLabel());
    } catch (RuntimeException e) {
      // Safe message only (no tokens, secrets, or raw payloads).
      log.warn("PayPal connectivity check failed: {}", e.getMessage());
      return new PayPalConnectionStatus("DOWN", properties.environmentLabel());
    }
  }
}
