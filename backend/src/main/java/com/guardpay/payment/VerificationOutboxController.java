package com.guardpay.payment;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local-only developer outbox for the simulated verification channel. This
 * exists so a human can retrieve the challenge without exposing it to the
 * browser or writing it to logs. It is disabled by default and rejects any
 * non-loopback caller.
 */
@RestController
@RequestMapping("/api/dev/verifications")
public class VerificationOutboxController {

  private final SimulatedVerificationOutbox outbox;
  private final PaymentVerificationRepository verifications;

  public VerificationOutboxController(
      SimulatedVerificationOutbox outbox,
      PaymentVerificationRepository verifications) {
    this.outbox = outbox;
    this.verifications = verifications;
  }

  @GetMapping("/{paymentId}/code")
  public ResponseEntity<DevVerificationCodeDto> readCode(
      @PathVariable UUID paymentId, HttpServletRequest request) {
    if (!isLoopback(request.getRemoteAddr()) || !outbox.enabled()) {
      return ResponseEntity.notFound().build();
    }
    Optional<PaymentVerification> verification = verifications.findByTransactionId(paymentId);
    if (verification.isEmpty()
        || verification.get().getStatus() != VerificationStatus.PENDING
        || verification.get().getExpiresAt() == null
        || OffsetDateTime.now(ZoneOffset.UTC).isAfter(verification.get().getExpiresAt())) {
      return ResponseEntity.notFound().build();
    }
    return outbox
        .read(paymentId)
        .map(code -> ResponseEntity.ok(new DevVerificationCodeDto(paymentId, code)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private static boolean isLoopback(String remoteAddress) {
    return "127.0.0.1".equals(remoteAddress)
        || "0:0:0:0:0:0:0:1".equals(remoteAddress)
        || "::1".equals(remoteAddress);
  }

  public record DevVerificationCodeDto(UUID paymentId, String code) {}
}
