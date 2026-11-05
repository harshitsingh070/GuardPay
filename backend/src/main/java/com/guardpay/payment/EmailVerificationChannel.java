package com.guardpay.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email OTP delivery for the controlled VERIFY challenge.
 *
 * <p>Uses Spring's mail starter, which auto-configures a sender only when
 * {@code spring.mail.host} is present. Without configuration this channel
 * reports itself unavailable and the service falls back to the clearly
 * labelled demo outbox. The code is never logged.
 */
@Component
public class EmailVerificationChannel {

  private static final Logger log =
      LoggerFactory.getLogger(EmailVerificationChannel.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final boolean enabled;
  private final String from;

  public EmailVerificationChannel(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${guardpay.verification.email-enabled:false}") boolean enabled,
      @Value("${guardpay.verification.email-from:no-reply@guardpay.local}") String from) {
    this.mailSender = mailSender;
    this.enabled = enabled;
    this.from = from;
  }

  public boolean isConfigured() {
    return enabled && mailSender.getIfAvailable() != null;
  }

  public void deliver(String recipientEmail, String code, long ttlMinutes) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (!enabled || sender == null) {
      throw new IllegalStateException("Email verification is not configured");
    }
    if (recipientEmail == null || recipientEmail.isBlank()) {
      throw new IllegalStateException("No recipient email is available");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipientEmail);
    message.setSubject("GuardPay verification code");
    message.setText(
        "Your GuardPay verification code is "
            + code
            + ". It expires in "
            + ttlMinutes
            + " minutes. If you did not start this payment, ignore this email and contact support.");
    sender.send(message);
    log.info("Verification code delivered over email");
  }
}
