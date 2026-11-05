package com.guardpay.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

  /** RFC 6238 SHA-1 test secret ("12345678901234567890"). */
  private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  private final TotpService totp = new TotpService();

  @Test
  void matchesRfc6238Vector() {
    assertEquals("287082", totp.code(SECRET, Instant.ofEpochSecond(59)));
  }

  @Test
  void acceptsAdjacentStepsForClockDrift() {
    Instant now = Instant.ofEpochSecond(1_111_111_111);
    String code = totp.code(SECRET, now.minusSeconds(TotpService.STEP_SECONDS));

    assertTrue(totp.verify(SECRET, code, now));
  }

  @Test
  void rejectsWrongAndMalformedCodes() {
    Instant now = Instant.ofEpochSecond(59);

    assertFalse(totp.verify(SECRET, "000000", now));
    assertFalse(totp.verify(SECRET, "28708", now));
    assertFalse(totp.verify(SECRET, null, now));
  }

  @Test
  void generatedSecretsRoundTrip() {
    String secret = totp.generateSecret();
    Instant now = Instant.ofEpochSecond(1_700_000_000);

    assertEquals(32, secret.length());
    assertTrue(totp.verify(secret, totp.code(secret, now), now));
  }
}
