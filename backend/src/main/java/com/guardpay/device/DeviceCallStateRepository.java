package com.guardpay.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Latest-state lookup is always scoped to the authenticated device owner. */
public interface DeviceCallStateRepository extends JpaRepository<DeviceCallState, UUID> {

  Optional<DeviceCallState> findByUserIdAndDeviceId(UUID userId, UUID deviceId);

  void deleteByUserIdAndDeviceId(UUID userId, UUID deviceId);
}
