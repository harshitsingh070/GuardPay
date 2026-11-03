package com.guardpay.recipient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.user.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Explicit recipient IDs must always resolve inside the owner's boundary. */
@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class RecipientRepositoryTest {

  @Autowired private RecipientRepository recipients;
  @Autowired private TestEntityManager entities;

  @Test
  void findByIdAndUserIdReturnsOwnRecipientAndHidesForeignRecipient() {
    User owner = user("owner");
    User stranger = user("stranger");
    Recipient own = recipient(owner, "Own");
    Recipient foreign = recipient(stranger, "Foreign");
    entities.flush();

    Optional<Recipient> ownResult = recipients.findByIdAndUserId(own.getId(), owner.getId());
    Optional<Recipient> foreignResult =
        recipients.findByIdAndUserId(foreign.getId(), owner.getId());

    assertTrue(ownResult.isPresent());
    assertEquals(own.getId(), ownResult.get().getId());
    assertTrue(foreignResult.isEmpty());
  }

  private User user(String externalId) {
    User user = new User();
    user.setExternalUserId(externalId);
    user.setEmail(externalId + "@example.com");
    user.setCurrency("USD");
    entities.persist(user);
    return user;
  }

  private Recipient recipient(User owner, String name) {
    Recipient recipient = new Recipient();
    recipient.setUser(owner);
    recipient.setName(name);
    entities.persist(recipient);
    return recipient;
  }
}
