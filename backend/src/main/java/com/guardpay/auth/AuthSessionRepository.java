package com.guardpay.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  @Query(
      """
      select s from AuthSession s join fetch s.user
      where s.tokenHash = :tokenHash and s.expiresAt > :now
      """)
  Optional<AuthSession> findActiveByTokenHash(
      @Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

  @Modifying
  @Query("delete from AuthSession s where s.tokenHash = :tokenHash")
  int deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
