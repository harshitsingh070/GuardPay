package com.guardpay.intelligence.config;

import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Makes the admin review console reachable in deployments that have no manual
 * database access. Admin emails are configured server-side only and are never
 * accepted from a client.
 */
@Component
public class RecipientIntelligenceAdminBootstrap {

  private static final Logger log =
      LoggerFactory.getLogger(RecipientIntelligenceAdminBootstrap.class);

  private final RecipientIntelligenceProperties properties;
  private final UserRepository users;

  public RecipientIntelligenceAdminBootstrap(
      RecipientIntelligenceProperties properties, UserRepository users) {
    this.properties = properties;
    this.users = users;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void promoteConfiguredAdmins() {
    if (!properties.isEnabled()) {
      return;
    }
    List<String> configured = properties.getAdminEmails();
    if (configured == null || configured.isEmpty()) {
      log.info(
          "No recipient intelligence admins configured; set "
              + "GUARDPAY_INTELLIGENCE_ADMIN_EMAILS to enable the review console");
      return;
    }
    for (String rawEmail : configured) {
      String email = rawEmail == null ? "" : rawEmail.trim();
      if (email.isEmpty()) {
        continue;
      }
      try {
        users
            .findByEmailIgnoreCase(email)
            .ifPresentOrElse(
                user -> promote(user, email),
                () -> log.warn("Configured intelligence admin {} has no GuardPay user", email));
      } catch (RuntimeException failure) {
        log.warn("Could not promote configured intelligence admin {}", email, failure);
      }
    }
  }

  private void promote(User user, String email) {
    if (user.isAdmin()) {
      log.info("Intelligence admin {} is already enabled", email);
      return;
    }
    user.setAdmin(true);
    users.save(user);
    log.info("Enabled intelligence admin {}", email);
  }
}
