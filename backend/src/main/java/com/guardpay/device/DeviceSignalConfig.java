package com.guardpay.device;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers {@link DeviceSignalProperties} without touching the entry point. */
@Configuration
@EnableConfigurationProperties(DeviceSignalProperties.class)
public class DeviceSignalConfig {}
