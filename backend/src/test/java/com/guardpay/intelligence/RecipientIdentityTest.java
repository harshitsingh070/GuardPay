package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guardpay.intelligence.domain.IdentifierType;
import com.guardpay.intelligence.identity.CanonicalIdentifier;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.identity.RecipientIdentifierNormalizer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipientIdentityTest {

  private final RecipientIdentifierNormalizer normalizer = new RecipientIdentifierNormalizer();

  @Test
  void emailNormalizationIsCaseInsensitiveAndDeterministic() {
    CanonicalIdentifier first = normalizer.normalize(IdentifierType.EMAIL, "  User@Example.COM ");
    CanonicalIdentifier second = normalizer.normalize(IdentifierType.EMAIL, "user@example.com");

    assertEquals("user@example.com", first.canonicalValue());
    assertEquals(first, second);
    assertEquals(RecipientIdentifierNormalizer.EMAIL_VERSION, first.normalizationVersion());
  }

  @Test
  void invalidIdentifiersAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> normalizer.normalize(IdentifierType.EMAIL, "not-an-email"));
    assertThrows(
        IllegalArgumentException.class,
        () -> normalizer.normalize(IdentifierType.DOMAIN, "127.0.0.1"));
  }

  @Test
  void hasherProducesStableDigestsAndSupportsKeyRotation() {
    CanonicalIdentifier identifier =
        normalizer.normalize(IdentifierType.EMAIL, "user@example.com");
    RecipientIdentifierHasher hasher =
        new RecipientIdentifierHasher(Map.of("1", "secret-one", "2", "secret-two"), 2);

    assertTrue(hasher.isConfigured());
    assertEquals(2, hasher.activeKeyVersion());
    assertEquals(64, hasher.activeDigest(identifier).digest().length());
    assertEquals(2, hasher.digests(identifier).size());
    assertEquals(hasher.activeDigest(identifier), hasher.digests(identifier).get(0));
  }

  @Test
  void blankKeyLeavesTheHasherUnconfigured() {
    RecipientIdentifierHasher hasher = new RecipientIdentifierHasher(Map.of("1", " "), 1);

    assertFalse(hasher.isConfigured());
    assertTrue(hasher.digests(normalizer.normalize(IdentifierType.EMAIL, "a@b.co")).isEmpty());
  }

  @Test
  void differentKeyVersionsProduceDifferentDigests() {
    CanonicalIdentifier identifier =
        normalizer.normalize(IdentifierType.EMAIL, "user@example.com");
    RecipientIdentifierHasher hasher =
        new RecipientIdentifierHasher(Map.of("1", "one", "2", "two"), 1);

    assertNotEquals(
        hasher.digests(identifier).get(0).digest(),
        hasher.digests(identifier).get(1).digest());
  }
}
