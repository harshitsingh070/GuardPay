package com.guardpay.payment;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Demo-only out-of-band channel. The code is held in server memory and exposed
 * only through a local-only developer endpoint; it is never logged, returned by
 * the public payment API, or stored in browser state. Disable this outbox when
 * a real delivery channel is configured.
 */
@Component
public class SimulatedVerificationOutbox implements VerificationChannel {

  private final ConcurrentMap<UUID, String> codes = new ConcurrentHashMap<>();
  private final boolean enabled;

  public SimulatedVerificationOutbox(
      @Value("${guardpay.verification.dev-outbox-enabled:false}") boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public void deliver(UUID paymentId, String code) {
    if (!enabled) {
      throw new IllegalStateException(
          "No verification delivery channel is configured");
    }
    codes.put(paymentId, code);
  }

  public Optional<String> read(UUID paymentId) {
    if (!enabled) {
      return Optional.empty();
    }
    return Optional.ofNullable(codes.get(paymentId));
  }

  @Override
  public boolean has(UUID paymentId) {
    return enabled && codes.containsKey(paymentId);
  }

  @Override
  public void clear(UUID paymentId) {
    codes.remove(paymentId);
  }

  public boolean enabled() {
    return enabled;
  }
}
