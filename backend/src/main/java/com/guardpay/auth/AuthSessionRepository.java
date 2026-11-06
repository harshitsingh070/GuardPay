package com.guardpay.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  @Query(
      """
      select s from AuthSession s join fetch s.user
      where s.tokenHash = :tokenHash
        and s.expiresAt > :now
        and s.absoluteExpiresAt > :now
      """)
  Optional<AuthSession> findActiveByTokenHash(
      @Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

  List<AuthSession> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);

  @Modifying
  @Query("delete from AuthSession s where s.tokenHash = :tokenHash")
  int deleteByTokenHash(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query("delete from AuthSession s where s.user.id = :userId")
  int deleteByUserId(@Param("userId") UUID userId);

  @Transactional
  @Modifying
  @Query(
      """
      update AuthSession s
      set s.lastUsedAt = :now,
          s.expiresAt = case
              when :slidingExpiry < s.absoluteExpiresAt then :slidingExpiry
              else s.absoluteExpiresAt
          end
      where s.id = :id
        and s.absoluteExpiresAt > :now
      """)
  int touch(
      @Param("id") UUID id,
      @Param("now") OffsetDateTime now,
      @Param("slidingExpiry") OffsetDateTime slidingExpiry);

  @Transactional
  @Modifying
  @Query("delete from AuthSession s where s.expiresAt <= :now or s.absoluteExpiresAt <= :now")
  int deleteExpired(@Param("now") OffsetDateTime now);
}
