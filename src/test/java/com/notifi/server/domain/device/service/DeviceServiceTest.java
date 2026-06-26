package com.notifi.server.domain.device.service;

import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.exception.DeviceErrorCode;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepository;

    @InjectMocks DeviceService deviceService;

    @Test
    @DisplayName("heartbeat: 등록된 device_uid → last_seen_at 갱신")
    void heartbeat_success() {
        Device device = Device.create(1L, "AA:BB:CC", null, null, null, null);
        given(deviceRepository.findByDeviceUid("AA:BB:CC")).willReturn(Optional.of(device));

        deviceService.heartbeat("AA:BB:CC");

        assertThat(device.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("heartbeat: 존재하지 않는 device_uid → DEVICE_NOT_FOUND")
    void heartbeat_notFound_throws() {
        given(deviceRepository.findByDeviceUid("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.heartbeat("UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }
}
