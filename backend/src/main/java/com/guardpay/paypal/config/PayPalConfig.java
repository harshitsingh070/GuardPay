package com.guardpay.paypal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link PayPalProperties} without touching the application entry point. */
@Configuration
@EnableConfigurationProperties(PayPalProperties.class)
public class PayPalConfig {}
