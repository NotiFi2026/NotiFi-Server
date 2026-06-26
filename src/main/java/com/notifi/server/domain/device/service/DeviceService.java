package com.notifi.server.domain.device.service;

import com.notifi.server.domain.device.exception.DeviceErrorCode;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    @Transactional
    public void heartbeat(String deviceUid) {
        deviceRepository.findByDeviceUid(deviceUid)
                .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND))
                .recordHeartbeat(Instant.now());
    }
}
