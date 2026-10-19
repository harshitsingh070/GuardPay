package com.guardpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Init scaffold smoke test — intentionally empty.
 * Real tests (risk calculation, profiles, auth, paypal, webhooks)
 * arrive in later milestones.
 */
class GuardPayApplicationTests {

  @Test
  void contextLoadsPlaceholder() {
    // No application context started yet on purpose (no datasource/entities).
  }
}
