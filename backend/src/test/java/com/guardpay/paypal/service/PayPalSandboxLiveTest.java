package com.guardpay.paypal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Manual live check — NOT part of the normal suite.
 *
 * <p>To run: start PostgreSQL, export {@code PAYPAL_CLIENT_ID} and
 * {@code PAYPAL_CLIENT_SECRET} (Sandbox), remove {@code @Disabled}, and run
 * this single test. Never commit credentials.
 */
@SpringBootTest
@Disabled("Manual only: requires Postgres plus real PayPal Sandbox credentials in the environment")
class PayPalSandboxLiveTest {

  @Autowired private PayPalService service;

  @Test
  void liveSandboxReportsUp() {
    assertEquals("UP", service.checkConnection().status());
  }
}
