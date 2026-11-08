package com.guardpay.behavior;

import com.guardpay.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-device behavior for one user. */
public interface UserDeviceBehaviorRepository
    extends JpaRepository<UserDeviceBehavior, UUID> {

  Optional<UserDeviceBehavior> findByUserIdAndDeviceIdAndDeviceFingerprint(
      UUID userId, UUID deviceId, String deviceFingerprint);

  void deleteByUserId(UUID userId);

  List<UserDeviceBehavior> findByUserIdOrderByTransactionCountDesc(
      UUID userId, Pageable pageable);

  @Query(
      """
      select count(distinct d.deviceId) from UserDeviceBehavior d
      where d.user.id = :userId
        and d.deviceId is not null
      """)
  Long countDistinctDevices(@Param("userId") UUID userId);
}
