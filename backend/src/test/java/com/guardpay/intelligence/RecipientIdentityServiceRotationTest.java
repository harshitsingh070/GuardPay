package com.guardpay.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guardpay.intelligence.domain.GlobalRecipientEntity;
import com.guardpay.intelligence.domain.IdentifierType;
import com.guardpay.intelligence.domain.IdentifierVerificationStatus;
import com.guardpay.intelligence.domain.IdentityConfidence;
import com.guardpay.intelligence.domain.RecipientIdentifier;
import com.guardpay.intelligence.identity.CanonicalIdentifier;
import com.guardpay.intelligence.identity.RecipientIdentifierHasher;
import com.guardpay.intelligence.identity.RecipientIdentifierNormalizer;
import com.guardpay.intelligence.repository.GlobalRecipientEntityRepository;
import com.guardpay.intelligence.repository.RecipientIdentifierRepository;
import com.guardpay.intelligence.repository.RecipientRelationshipRepository;
import com.guardpay.intelligence.service.RecipientIdentityService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecipientIdentityServiceRotationTest {

  @Mock private GlobalRecipientEntityRepository entities;
  @Mock private RecipientIdentifierRepository identifiers;
  @Mock private RecipientRelationshipRepository relationships;

  @Test
  void rotationReusesEntityAndAddsActiveDigest() {
    RecipientIdentifierNormalizer normalizer = new RecipientIdentifierNormalizer();
    RecipientIdentifierHasher hasher =
        new RecipientIdentifierHasher(Map.of("1", "secret-one", "2", "secret-two"), 2);
    RecipientIdentityService service =
        new RecipientIdentityService(normalizer, hasher, entities, identifiers, relationships);
    CanonicalIdentifier identifier =
        normalizer.normalize(IdentifierType.EMAIL, "user@example.com");

    String oldDigest =
        new RecipientIdentifierHasher(Map.of("1", "secret-one"), 1)
            .activeDigest(identifier)
            .digest();
    String activeDigest = hasher.activeDigest(identifier).digest();

    GlobalRecipientEntity entity = new GlobalRecipientEntity();
    ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
    entity.setIdentityConfidence(IdentityConfidence.MEDIUM);
    RecipientIdentifier oldRow = new RecipientIdentifier();
    oldRow.setRecipientEntity(entity);
    oldRow.setIdentifierType(IdentifierType.EMAIL);
    oldRow.setIdentifierHmac(oldDigest);
    oldRow.setHmacKeyVersion(1);
    oldRow.setNormalizationVersion(identifier.normalizationVersion());
    oldRow.setIdentityConfidence(IdentityConfidence.MEDIUM);

    when(identifiers.findByIdentifierTypeAndIdentifierHmac(
            IdentifierType.EMAIL, activeDigest))
        .thenReturn(Optional.empty());
    when(identifiers.findByIdentifierTypeAndIdentifierHmac(IdentifierType.EMAIL, oldDigest))
        .thenReturn(Optional.of(oldRow));

    Optional<GlobalRecipientEntity> resolved =
        service.resolveOrCreate(identifier, IdentifierVerificationStatus.UNVERIFIED, IdentityConfidence.MEDIUM);

    assertTrue(resolved.isPresent());
    assertEquals(entity.getId(), resolved.get().getId());

    ArgumentCaptor<RecipientIdentifier> captor =
        ArgumentCaptor.forClass(RecipientIdentifier.class);
    verify(identifiers, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    assertTrue(
        captor.getAllValues().stream()
            .anyMatch(
                row ->
                    activeDigest.equals(row.getIdentifierHmac())
                        && row.getHmacKeyVersion() == 2
                        && row.getRecipientEntity() == entity));
  }
}
