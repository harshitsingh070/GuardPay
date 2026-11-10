package com.guardpay.intelligence.service;

import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.IdentifierMatchLevel;
import com.guardpay.intelligence.domain.IdentifierType;
import com.guardpay.intelligence.domain.IdentifierVerificationStatus;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.RecipientIdentifier;
import com.guardpay.intelligence.domain.RecipientRelationship;
import com.guardpay.intelligence.domain.RelationshipConfidence;
import com.guardpay.intelligence.domain.RelationshipType;
import com.guardpay.intelligence.identity.CanonicalIdentifier;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher.HmacDigest;
import com.guardpay.intelligence.identity.RecipientIdentifierNormalizer;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientIdentifierRepository;
import com.guardpay.intelligence.repository.RecipientRelationshipRepository;
import com.guardpay.recipient.Recipient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a user-scoped {@link Recipient} to a shared global entity.
 *
 * <p>Only HMAC digests are persisted. When the HMAC key is not configured the
 * service returns empty instead of writing an unkeyed identifier.
 */
@Service
public class RecipientIdentityService {

  private final RecipientIdentifierNormalizer normalizer;
  private final RecipientIdentifierHasher hasher;
  private final GlobalRecipientEntityRepository entities;
  private final RecipientIdentifierRepository identifiers;
  private final RecipientRelationshipRepository relationships;

  public RecipientIdentityService(
      RecipientIdentifierNormalizer normalizer,
      RecipientIdentifierHasher hasher,
      GlobalRecipientEntityRepository entities,
      RecipientIdentifierRepository identifiers,
      RecipientRelationshipRepository relationships) {
    this.normalizer = normalizer;
    this.hasher = hasher;
    this.entities = entities;
    this.identifiers = identifiers;
    this.relationships = relationships;
  }

  public boolean isConfigured() {
    return hasher.isConfigured();
  }

  /** Read-only lookup used by payment evaluation; never creates rows. */
  @Transactional(readOnly = true)
  public Optional<IdentityMatch> findForRecipient(Recipient recipient) {
    if (!hasher.isConfigured() || recipient == null) {
      return Optional.empty();
    }
    IdentityMatch best = null;
    for (Candidate candidate : candidates(recipient)) {
      for (HmacDigest digest : hasher.digests(candidate.identifier)) {
        Optional<RecipientIdentifier> row =
            identifiers.findByIdentifierTypeAndIdentifierHmac(
                candidate.identifier.type(), digest.digest());
        if (row.isEmpty()) {
          continue;
        }
        RecipientIdentifier identifier = row.get();
        IdentityMatch match = matchOf(identifier, candidate);
        if (best == null || rank(match) > rank(best)) {
          best = match;
        }
      }
    }
    return Optional.ofNullable(best);
  }

  /** Resolves (creating when needed) the global entity for a recipient. */
  @Transactional
  public Optional<IdentityMatch> resolveForRecipient(Recipient recipient) {
    if (!hasher.isConfigured() || recipient == null) {
      return Optional.empty();
    }
    List<Candidate> candidates = candidates(recipient);
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    Candidate primary = candidates.get(0);
    GlobalRecipientEntity entity =
        resolveOrCreate(
                primary.identifier,
                IdentifierVerificationStatus.UNVERIFIED,
                primary.confidence)
            .orElse(null);
    if (entity == null) {
      return Optional.empty();
    }
    for (int i = 1; i < candidates.size(); i++) {
      linkIfAbsent(entity, candidates.get(i));
    }
    IdentityMatch match =
        new IdentityMatch(
            entity,
            IdentifierMatchLevel.EXACT_UNVERIFIED_MATCH,
            primary.confidence,
            primary.identifier.type());
    upsertRelationship(recipient.getUser().getId(), entity, match.confidence());
    return Optional.of(match);
  }

  /** Resolves a single explicit identifier for report submission. */
  @Transactional
  public Optional<IdentityMatch> resolveIdentifier(
      IdentifierType type,
      String rawValue,
      IdentifierVerificationStatus verification,
      IdentityConfidence confidence) {
    if (!hasher.isConfigured()) {
      return Optional.empty();
    }
    CanonicalIdentifier identifier = normalizer.normalize(type, rawValue);
    return resolveOrCreate(identifier, verification, confidence)
        .map(
            entity ->
                new IdentityMatch(
                    entity,
                    verification == IdentifierVerificationStatus.VERIFIED
                        ? IdentifierMatchLevel.EXACT_VERIFIED_MATCH
                        : IdentifierMatchLevel.EXACT_UNVERIFIED_MATCH,
                    confidence,
                    type));
  }

  @Transactional
  public Optional<GlobalRecipientEntity> resolveOrCreate(
      CanonicalIdentifier identifier,
      IdentifierVerificationStatus verification,
      IdentityConfidence confidence) {
    if (!hasher.isConfigured()) {
      return Optional.empty();
    }
    HmacDigest active = hasher.activeDigest(identifier);
    Optional<RecipientIdentifier> existing =
        identifiers.findByIdentifierTypeAndIdentifierHmac(
            identifier.type(), active.digest());
    if (existing.isPresent()) {
      RecipientIdentifier row = existing.get();
      touch(row, verification, confidence);
      GlobalRecipientEntity entity = row.getRecipientEntity();
      if (confidence.ordinal() > entity.getIdentityConfidence().ordinal()) {
        entity.setIdentityConfidence(confidence);
        entities.save(entity);
      }
      return Optional.of(entity);
    }
    GlobalRecipientEntity entity = new GlobalRecipientEntity();
    entity.setIdentityConfidence(confidence);
    entities.save(entity);
    RecipientIdentifier row = new RecipientIdentifier();
    row.setRecipientEntity(entity);
    row.setIdentifierType(identifier.type());
    row.setIdentifierHmac(active.digest());
    row.setHmacKeyVersion(active.keyVersion());
    row.setNormalizationVersion(identifier.normalizationVersion());
    row.setVerificationStatus(verification);
    row.setIdentityConfidence(confidence);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    row.setFirstSeenAt(now);
    row.setLastSeenAt(now);
    identifiers.save(row);
    return Optional.of(entity);
  }

  private void linkIfAbsent(GlobalRecipientEntity entity, Candidate candidate) {
    HmacDigest active = hasher.activeDigest(candidate.identifier);
    Optional<RecipientIdentifier> existing =
        identifiers.findByIdentifierTypeAndIdentifierHmac(
            candidate.identifier.type(), active.digest());
    if (existing.isPresent()) {
      touch(existing.get(), IdentifierVerificationStatus.UNVERIFIED, candidate.confidence);
      return;
    }
    RecipientIdentifier row = new RecipientIdentifier();
    row.setRecipientEntity(entity);
    row.setIdentifierType(candidate.identifier.type());
    row.setIdentifierHmac(active.digest());
    row.setHmacKeyVersion(active.keyVersion());
    row.setNormalizationVersion(candidate.identifier.normalizationVersion());
    row.setVerificationStatus(IdentifierVerificationStatus.UNVERIFIED);
    row.setIdentityConfidence(candidate.confidence);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    row.setFirstSeenAt(now);
    row.setLastSeenAt(now);
    identifiers.save(row);
  }

  private void touch(
      RecipientIdentifier row,
      IdentifierVerificationStatus verification,
      IdentityConfidence confidence) {
    row.setLastSeenAt(OffsetDateTime.now(ZoneOffset.UTC));
    if (verification == IdentifierVerificationStatus.VERIFIED
        && row.getVerificationStatus() != IdentifierVerificationStatus.VERIFIED) {
      row.setVerificationStatus(IdentifierVerificationStatus.VERIFIED);
    }
    if (confidence.ordinal() > row.getIdentityConfidence().ordinal()) {
      row.setIdentityConfidence(confidence);
    }
    identifiers.save(row);
  }

  private void upsertRelationship(
      UUID ownerReference,
      GlobalRecipientEntity entity,
      IdentityConfidence confidence) {
    if (ownerReference == null || entity.getId() == null) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    RecipientRelationship relationship =
        relationships
            .findByOwnerReferenceAndRecipientEntityId(ownerReference, entity.getId())
            .orElseGet(
                () -> {
                  RecipientRelationship created = new RecipientRelationship();
                  created.setOwnerReference(ownerReference);
                  created.setRecipientEntity(entity);
                  created.setRelationshipType(RelationshipType.PAYMENT_RECIPIENT);
                  created.setConfidence(relationshipConfidence(confidence));
                  created.setFirstSeenAt(now);
                  created.setInteractionCount(0);
                  return created;
                });
    relationship.setLastSeenAt(now);
    relationship.setInteractionCount(relationship.getInteractionCount() + 1);
    relationships.save(relationship);
  }

  private static RelationshipConfidence relationshipConfidence(IdentityConfidence confidence) {
    return switch (confidence) {
      case VERY_HIGH, HIGH -> RelationshipConfidence.HIGH;
      case MEDIUM -> RelationshipConfidence.MEDIUM;
      case LOW -> RelationshipConfidence.LOW;
    };
  }

  private static IdentityMatch matchOf(
      RecipientIdentifier identifier, Candidate candidate) {
    IdentifierMatchLevel level =
        identifier.getVerificationStatus() == IdentifierVerificationStatus.VERIFIED
            ? IdentifierMatchLevel.EXACT_VERIFIED_MATCH
            : IdentifierMatchLevel.EXACT_UNVERIFIED_MATCH;
    IdentityConfidence confidence =
        identifier.getIdentityConfidence().ordinal() > candidate.confidence.ordinal()
            ? identifier.getIdentityConfidence()
            : candidate.confidence;
    return new IdentityMatch(
        identifier.getRecipientEntity(), level, confidence, candidate.identifier.type());
  }

  private static double rank(IdentityMatch match) {
    double typePriority =
        match.matchedType() == IdentifierType.PAYPAL_ACCOUNT_ID ? 0.2 : 0.1;
    return match.matchLevel().multiplier()
        + typePriority
        + match.confidence().ordinal() * 0.01;
  }

  private List<Candidate> candidates(Recipient recipient) {
    List<Candidate> candidates = new ArrayList<>(2);
    if (recipient.getPaypalAccountId() != null
        && !recipient.getPaypalAccountId().isBlank()) {
      safeNormalize(IdentifierType.PAYPAL_ACCOUNT_ID, recipient.getPaypalAccountId())
          .ifPresent(
              identifier ->
                  candidates.add(new Candidate(identifier, IdentityConfidence.HIGH)));
    }
    if (recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
      safeNormalize(IdentifierType.EMAIL, recipient.getEmail())
          .ifPresent(
              identifier ->
                  candidates.add(new Candidate(identifier, IdentityConfidence.MEDIUM)));
    }
    return candidates;
  }

  private Optional<CanonicalIdentifier> safeNormalize(
      IdentifierType type, String value) {
    try {
      return Optional.of(normalizer.normalize(type, value));
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
  }

  private record Candidate(
      CanonicalIdentifier identifier, IdentityConfidence confidence) {}

  /** One resolved match plus how it was established. */
  public record IdentityMatch(
      GlobalRecipientEntity entity,
      IdentifierMatchLevel matchLevel,
      IdentityConfidence confidence,
      IdentifierType matchedType) {}
}
