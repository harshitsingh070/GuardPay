package com.guardpay.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Development device-pairing settings.
 *
 * <p>The signal token is a hackathon pairing mechanism, NOT production
 * security — production needs authenticated registration, short-lived
 * credentials, and device attestation. Never logged.
 */
@ConfigurationProperties(prefix = "guardpay.device")
public class DeviceSignalProperties {

  /** From {@code GUARDPAY_DEVICE_SIGNAL_TOKEN}. Blank means pairing disabled (all rejected). */
  private String signalToken;

  public String getSignalToken() {
    return signalToken;
  }

  public void setSignalToken(String signalToken) {
    this.signalToken = signalToken;
  }
}
