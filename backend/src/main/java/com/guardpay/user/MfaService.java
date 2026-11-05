package com.guardpay.user;

import com.guardpay.payment.TotpService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-account TOTP enrollment for the payment verification challenge. */
@Service
public class MfaService {

  private static final String ISSUER = "GuardPay";

  private final DemoUserService demoUser;
  private final UserRepository users;
  private final TotpService totp;

  public MfaService(DemoUserService demoUser, UserRepository users, TotpService totp) {
    this.demoUser = demoUser;
    this.users = users;
    this.totp = totp;
  }

  @Transactional(readOnly = true)
  public TotpEnrollment status() {
    User user = demoUser.currentUser();
    return new TotpEnrollment(
        user.getTotpSecret() != null && !user.getTotpSecret().isBlank(),
        null,
        null);
  }

  @Transactional
  public TotpEnrollment enroll() {
    User user = demoUser.currentUser();
    String secret = totp.generateSecret();
    user.setTotpSecret(secret);
    users.save(user);
    return new TotpEnrollment(
        true, secret, totp.otpauthUri(ISSUER, user.getEmail(), secret));
  }

  @Transactional
  public TotpEnrollment disable() {
    User user = demoUser.currentUser();
    user.setTotpSecret(null);
    users.save(user);
    return new TotpEnrollment(false, null, null);
  }

  /** Current TOTP secret, used only by payment verification (never serialized). */
  @Transactional(readOnly = true)
  public Optional<String> activeSecret() {
    String secret = demoUser.currentUser().getTotpSecret();
    return secret == null || secret.isBlank() ? Optional.empty() : Optional.of(secret);
  }

  /**
   * Enrollment result. The secret is returned only from the explicit demo
   * enrollment call so it can be added to an authenticator app.
   */
  public record TotpEnrollment(boolean enrolled, String secret, String otpauthUri) {}
}
