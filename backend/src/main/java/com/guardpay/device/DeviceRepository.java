package com.guardpay.device;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  Optional<Device> findByIdAndUserId(UUID id, UUID userId);

  Optional<Device> findByCredentialHashAndRevokedAtIsNull(String credentialHash);

  List<Device> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
