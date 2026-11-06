package com.guardpay.behavior;

import com.guardpay.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link UserBehaviorProfile}. Owner-scoped by user_id. */
public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, UUID> {

  Optional<UserBehaviorProfile> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  void deleteByUserId(UUID userId);
}
