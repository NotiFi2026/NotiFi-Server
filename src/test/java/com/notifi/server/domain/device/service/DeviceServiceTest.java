package com.notifi.server.domain.device.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.device.dto.DeviceCreateRequest;
import com.notifi.server.domain.device.dto.DeviceCreateResponse;
import com.notifi.server.domain.device.dto.DeviceResponse;
import com.notifi.server.domain.device.dto.DeviceUpdateRequest;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.entity.DeviceStatus;
import com.notifi.server.domain.device.entity.NodeRole;
import com.notifi.server.domain.device.exception.DeviceErrorCode;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock DeviceRepository deviceRepository;
    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;

    @InjectMocks DeviceService deviceService;

    // ── D1: register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: 정상 등록 → device_id 반환")
    void register_success() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);
        given(deviceRepository.existsByDeviceUid("AA:BB:CC:DD:EE:FF")).willReturn(false);
        given(deviceRepository.save(any())).willAnswer(inv -> {
            Device d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 10L);
            return d;
        });

        DeviceCreateRequest req = new DeviceCreateRequest("AA:BB:CC:DD:EE:FF", "거실", null, NodeRole.RECEIVER, null);
        DeviceCreateResponse result = deviceService.register(1L, 45L, req);

        assertThat(result.deviceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("register: device_uid 중복 → DEVICE_ALREADY_EXISTS")
    void register_duplicateUid_throws() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);
        given(deviceRepository.existsByDeviceUid("AA:BB:CC:DD:EE:FF")).willReturn(true);

        DeviceCreateRequest req = new DeviceCreateRequest("AA:BB:CC:DD:EE:FF", null, null, null, null);
        assertThatThrownBy(() -> deviceService.register(1L, 45L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("register: 관계 없고 노인 존재 → ACCESS_DENIED")
    void register_noRelationship_targetExists_accessDenied() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(false);
        given(careTargetRepository.existsById(45L)).willReturn(true);

        DeviceCreateRequest req = new DeviceCreateRequest("AA:BB:CC:DD:EE:FF", null, null, null, null);
        assertThatThrownBy(() -> deviceService.register(1L, 45L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("register: 동시 요청으로 DB unique 위반 → DEVICE_ALREADY_EXISTS")
    void register_concurrentDuplicate_throws() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);
        given(deviceRepository.existsByDeviceUid("AA:BB:CC:DD:EE:FF")).willReturn(false);
        given(deviceRepository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

        DeviceCreateRequest req = new DeviceCreateRequest("AA:BB:CC:DD:EE:FF", null, null, null, null);
        assertThatThrownBy(() -> deviceService.register(1L, 45L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("register: 노인 없음 → CARE_TARGET_NOT_FOUND")
    void register_targetNotFound() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 99L)).willReturn(false);
        given(careTargetRepository.existsById(99L)).willReturn(false);

        DeviceCreateRequest req = new DeviceCreateRequest("AA:BB:CC:DD:EE:FF", null, null, null, null);
        assertThatThrownBy(() -> deviceService.register(1L, 99L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));
    }

    // ── D2: list ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: 목록 2건 정상 반환 및 DTO 매핑")
    void list_success() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        Device d1 = Device.create(45L, "AA:BB:CC:DD:EE:FF", "거실", null, NodeRole.RECEIVER, null);
        Device d2 = Device.create(45L, "11:22:33:44:55:66", "침실", null, NodeRole.SENDER, null);
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of(d1, d2));

        List<DeviceResponse> result = deviceService.list(1L, 45L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).deviceUid()).isEqualTo("AA:BB:CC:DD:EE:FF");
        assertThat(result.get(0).status()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(result.get(1).deviceUid()).isEqualTo("11:22:33:44:55:66");
    }

    // ── D3: update ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: null 필드는 기존 값 유지 (부분수정)")
    void update_partial_preservesNull() {
        Device device = Device.create(45L, "AA:BB:CC", "거실", null, NodeRole.RECEIVER, null);
        ReflectionTestUtils.setField(device, "id", 10L);

        given(deviceRepository.findById(10L)).willReturn(Optional.of(device));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        DeviceUpdateRequest req = new DeviceUpdateRequest("침실", null, null, DeviceStatus.INACTIVE);
        DeviceResponse result = deviceService.update(1L, 10L, req);

        assertThat(result.room()).isEqualTo("침실");
        assertThat(result.status()).isEqualTo(DeviceStatus.INACTIVE);
        assertThat(result.nodeRole()).isEqualTo(NodeRole.RECEIVER); // null 전달 → 기존 유지
    }

    @Test
    @DisplayName("update: 존재하지 않는 deviceId → DEVICE_NOT_FOUND")
    void update_notFound_throws() {
        given(deviceRepository.findById(999L)).willReturn(Optional.empty());

        DeviceUpdateRequest req = new DeviceUpdateRequest("침실", null, null, null);
        assertThatThrownBy(() -> deviceService.update(1L, 999L, req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    // ── D4: delete ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: 정상 삭제 → deviceRepository.delete 호출")
    void delete_success() {
        Device device = Device.create(45L, "AA:BB:CC", null, null, null, null);
        ReflectionTestUtils.setField(device, "id", 10L);

        given(deviceRepository.findById(10L)).willReturn(Optional.of(device));
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        deviceService.delete(1L, 10L);

        verify(deviceRepository).delete(device);
    }

    @Test
    @DisplayName("delete: 존재하지 않는 deviceId → DEVICE_NOT_FOUND")
    void delete_notFound_throws() {
        given(deviceRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    // ── I4: heartbeat (기존 유지) ─────────────────────────────────────────────

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
