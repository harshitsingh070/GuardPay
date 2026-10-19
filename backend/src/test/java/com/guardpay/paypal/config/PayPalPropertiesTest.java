package com.guardpay.paypal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PayPalPropertiesTest {

  @Test
  void sandboxIsDefaultAndLabeled() {
    PayPalProperties properties = new PayPalProperties();

    assertEquals("https://api-m.sandbox.paypal.com", properties.getBaseUrl());
    assertEquals("SANDBOX", properties.environmentLabel());
  }

  @Test
  void liveUrlIsLabeledLive() {
    PayPalProperties properties = new PayPalProperties();
    properties.setBaseUrl("https://api-m.paypal.com");

    assertEquals("LIVE", properties.environmentLabel());
  }
}
