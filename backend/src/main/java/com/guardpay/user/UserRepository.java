package com.guardpay.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Basic persistence for {@link User} plus the fixed demo-account lookup. */
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByExternalUserId(String externalUserId);
}
