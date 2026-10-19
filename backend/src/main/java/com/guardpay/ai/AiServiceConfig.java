package com.guardpay.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link AiServiceProperties} without touching the entry point. */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceConfig {}
