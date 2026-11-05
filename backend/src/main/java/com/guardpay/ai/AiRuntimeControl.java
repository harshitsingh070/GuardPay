package com.guardpay.ai;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Runtime demo switch for the AI evidence service. Toggling it off does not
 * stop the deterministic engine, which is exactly the resilience story the
 * dashboard needs to show.
 */
@Component
public class AiRuntimeControl {

  private final AtomicBoolean disabled = new AtomicBoolean(false);

  public boolean isDisabled() {
    return disabled.get();
  }

  public boolean isEnabled() {
    return !disabled.get();
  }

  public void setEnabled(boolean enabled) {
    disabled.set(!enabled);
  }
}
