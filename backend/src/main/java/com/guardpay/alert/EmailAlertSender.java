package com.guardpay.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Email delivery for CRITICAL alerts; unavailable unless mail is configured. */
@Component
public class EmailAlertSender {

  private static final Logger log = LoggerFactory.getLogger(EmailAlertSender.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final boolean enabled;
  private final String from;

  public EmailAlertSender(
      ObjectProvider<JavaMailSender> mailSender,
      @Value("${guardpay.alerts.email-enabled:true}") boolean enabled,
      @Value("${guardpay.alerts.email-from:no-reply@guardpay.local}") String from) {
    this.mailSender = mailSender;
    this.enabled = enabled;
    this.from = from;
  }

  public boolean isConfigured() {
    return enabled && mailSender.getIfAvailable() != null;
  }

  public void send(Alert alert, String email) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (!isConfigured() || sender == null) {
      throw new IllegalStateException("Email alerts are not configured");
    }
    if (email == null || email.isBlank()) {
      throw new IllegalStateException("No user email is available");
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject("GuardPay alert: " + alert.getTitle());
    message.setText(
        alert.getMessage()
            + System.lineSeparator()
            + System.lineSeparator()
            + "This is a hackathon prototype alert. Verify the payment in GuardPay before taking action.");
    sender.send(message);
    log.info("Alert email delivered");
  }
}
