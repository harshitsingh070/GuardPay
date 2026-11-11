package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guardpay.intelligence.config.RecipientIntelligenceAdminBootstrap;
import com.guardpay.intelligence.config.RecipientIntelligenceProperties;
import com.guardpay.user.User;
import com.guardpay.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipientIntelligenceAdminBootstrapTest {

  private final UserRepository users = mock(UserRepository.class);

  @Test
  void configuredEmailsAreTrimmedAndPromoted() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setAdminEmails(List.of(" Admin@Example.com "));
    User user = new User();
    user.setAdmin(false);
    when(users.findByEmailIgnoreCase("Admin@Example.com")).thenReturn(Optional.of(user));

    new RecipientIntelligenceAdminBootstrap(properties, users).promoteConfiguredAdmins();

    assertTrue(user.isAdmin());
    verify(users).save(user);
  }

  @Test
  void existingAdminIsLeftUntouched() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setAdminEmails(List.of("admin@example.com"));
    User user = new User();
    user.setAdmin(true);
    when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));

    new RecipientIntelligenceAdminBootstrap(properties, users).promoteConfiguredAdmins();

    assertTrue(user.isAdmin());
    verify(users).findByEmailIgnoreCase("admin@example.com");
    verify(users, never()).save(any(User.class));
  }

  @Test
  void disabledIntelligenceDoesNotPromoteAnyone() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setEnabled(false);
    properties.setAdminEmails(List.of("admin@example.com"));

    new RecipientIntelligenceAdminBootstrap(properties, users).promoteConfiguredAdmins();

    verifyNoInteractions(users);
  }

  @Test
  void unknownConfiguredEmailIsIgnored() {
    RecipientIntelligenceProperties properties = new RecipientIntelligenceProperties();
    properties.setAdminEmails(List.of("missing@example.com"));
    when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    new RecipientIntelligenceAdminBootstrap(properties, users).promoteConfiguredAdmins();

    verify(users).findByEmailIgnoreCase("missing@example.com");
    verify(users, never()).save(any(User.class));
    assertFalse(properties.getAdminEmails().isEmpty());
  }
}
