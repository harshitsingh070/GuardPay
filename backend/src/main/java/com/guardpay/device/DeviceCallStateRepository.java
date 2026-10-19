package com.guardpay.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Latest-state lookup per device. One row per device by unique constraint. */
public interface DeviceCallStateRepository extends JpaRepository<DeviceCallState, UUID> {

  Optional<DeviceCallState> findByDeviceId(UUID deviceId);
}
