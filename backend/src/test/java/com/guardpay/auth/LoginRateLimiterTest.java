package com.guardpay.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LoginRateLimiterTest {

  private static final String EMAIL = "sam@example.com";
  private static final String IP = "203.0.113.10";

  @Test
  void blocksCredentialAfterConfiguredFailures() {
    LoginRateLimiter limiter =
        new LoginRateLimiter(2, 20, Duration.ofMinutes(15), Duration.ofMinutes(15), fixedClock());

    limiter.recordFailure(EMAIL, IP);
    assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, IP));

    limiter.recordFailure(EMAIL, IP);
    ResponseStatusException blocked =
        assertThrows(
            ResponseStatusException.class, () -> limiter.checkAllowed(EMAIL, IP));

    assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
  }

  @Test
  void blocksSourceIpAcrossDifferentEmails() {
    LoginRateLimiter limiter =
        new LoginRateLimiter(10, 2, Duration.ofMinutes(15), Duration.ofMinutes(15), fixedClock());

    limiter.recordFailure("first@example.com", IP);
    limiter.recordFailure("second@example.com", IP);

    ResponseStatusException blocked =
        assertThrows(
            ResponseStatusException.class,
            () -> limiter.checkAllowed("third@example.com", IP));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());
  }

  @Test
  void successfulLoginClearsTheCredentialCounter() {
    LoginRateLimiter limiter =
        new LoginRateLimiter(2, 20, Duration.ofMinutes(15), Duration.ofMinutes(15), fixedClock());

    limiter.recordFailure(EMAIL, IP);
    limiter.reset(EMAIL, IP);
    limiter.recordFailure(EMAIL, IP);

    assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, IP));
  }

  @Test
  void lockoutExpiresAfterTheConfiguredWindow() {
    MutableClock clock = new MutableClock(Instant.parse("2026-11-05T00:00:00Z"));
    LoginRateLimiter limiter =
        new LoginRateLimiter(1, 20, Duration.ofMinutes(15), Duration.ofMinutes(5), clock);

    limiter.recordFailure(EMAIL, IP);
    assertThrows(ResponseStatusException.class, () -> limiter.checkAllowed(EMAIL, IP));

    clock.advance(Duration.ofMinutes(5).plusSeconds(1));

    assertDoesNotThrow(() -> limiter.checkAllowed(EMAIL, IP));
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
