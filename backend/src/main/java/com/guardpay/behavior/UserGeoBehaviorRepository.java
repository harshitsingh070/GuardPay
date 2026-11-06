package com.guardpay.behavior;

import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-geo-country behavior for one user. */
public interface UserGeoBehaviorRepository
    extends JpaRepository<UserGeoBehavior, UUID> {

  Optional<UserGeoBehavior> findByUserIdAndGeoCountry(UUID userId, String geoCountry);

  List<UserGeoBehavior> findByUserIdOrderByTransactionCountDesc(
      UUID userId, Pageable pageable);
}
