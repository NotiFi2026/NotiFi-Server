package com.notifi.server.domain.device.repository;

import com.notifi.server.domain.device.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceUid(String deviceUid);
}
