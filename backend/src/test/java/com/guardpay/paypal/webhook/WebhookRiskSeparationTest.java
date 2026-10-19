package com.guardpay.paypal.webhook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Structural guard for the core security rule: webhooks can never change the
 * GuardPay risk decision. No webhook-layer type may reference
 * {@code com.guardpay.risk} at all — risk state is simply unreachable.
 */
class WebhookRiskSeparationTest {

  private static final Class<?>[] WEBHOOK_TYPES = {
    PayPalWebhookService.class,
    PayPalWebhookVerifier.class,
    PayPalWebhookController.class,
    PayPalWebhookEvent.class,
    PayPalWebhookEventRepository.class,
    WebhookProcessingStatus.class
  };

  @Test
  void webhookLayerNeverTouchesRiskPackage() {
    Set<String> violations = new HashSet<>();
    for (Class<?> type : WEBHOOK_TYPES) {
      for (Field field : type.getDeclaredFields()) {
        check(violations, type, field.getType());
      }
      for (Method method : type.getDeclaredMethods()) {
        if (method.isSynthetic() || method.isBridge()) {
          continue;
        }
        check(violations, type, method.getReturnType());
        for (Class<?> param : method.getParameterTypes()) {
          check(violations, type, param);
        }
      }
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        for (Class<?> param : constructor.getParameterTypes()) {
          check(violations, type, param);
        }
      }
    }
    assertTrue(
        violations.isEmpty(), () -> "Risk references inside webhook layer: " + violations);
    assertFalse(Modifier.isAbstract(PayPalWebhookService.class.getModifiers()));
  }

  private static void check(Set<String> violations, Class<?> owner, Class<?> used) {
    if (used != null
        && used.getPackageName() != null
        && used.getPackageName().startsWith("com.guardpay.risk")) {
      violations.add(owner.getSimpleName() + " -> " + used.getName());
    }
  }
}
