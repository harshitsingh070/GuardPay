package com.guardpay.intelligence.identity;

import com.guardpay.intelligence.domain.IdentifierType;
import java.util.Objects;

/**
 * A normalized identifier ready for HMAC hashing.
 *
 * <p>The canonical value never leaves this layer: only the HMAC digest is
 * persisted. {@code normalizationVersion} is part of the HMAC input so a
 * future normalization change cannot silently collide with old digests.
 */
public record CanonicalIdentifier(
    IdentifierType type, String canonicalValue, String normalizationVersion) {

  public CanonicalIdentifier {
    Objects.requireNonNull(type, "type must not be null");
    Objects.requireNonNull(normalizationVersion, "normalizationVersion must not be null");
    if (canonicalValue == null || canonicalValue.isBlank()) {
      throw new IllegalArgumentException("identifier value must not be blank");
    }
  }
}
