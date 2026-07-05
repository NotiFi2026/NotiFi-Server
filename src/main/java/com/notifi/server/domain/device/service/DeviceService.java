package com.notifi.server.domain.device.service;

import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.device.dto.DeviceCreateRequest;
import com.notifi.server.domain.device.dto.DeviceCreateResponse;
import com.notifi.server.domain.device.dto.DeviceResponse;
import com.notifi.server.domain.device.dto.DeviceUpdateRequest;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.exception.DeviceErrorCode;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CareTargetAccessValidator accessValidator;

    // ── D1: 노드 등록 ─────────────────────────────────────────────────────────
    @Transactional
    public DeviceCreateResponse register(Long userId, Long careTargetId, DeviceCreateRequest request) {
        accessValidator.requireRelationship(userId, careTargetId);

        if (deviceRepository.existsByDeviceUid(request.deviceUid())) {
            throw new BusinessException(DeviceErrorCode.DEVICE_ALREADY_EXISTS);
        }

        try {
            Device device = deviceRepository.save(Device.create(
                    careTargetId,
                    request.deviceUid(),
                    request.room(),
                    request.positionLabel(),
                    request.nodeRole(),
                    request.firmwareVersion()
            ));
            return new DeviceCreateResponse(device.getId());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 경합으로 device_uid unique 위반 시 409로 변환
            throw new BusinessException(DeviceErrorCode.DEVICE_ALREADY_EXISTS);
        }
    }

    // ── D2: 노드 목록 조회 ────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<DeviceResponse> list(Long userId, Long careTargetId) {
        accessValidator.requireRelationship(userId, careTargetId);
        return deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(careTargetId)
                .stream()
                .map(DeviceResponse::from)
                .toList();
    }

    // ── D3: 노드 정보 수정 ────────────────────────────────────────────────────
    @Transactional
    public DeviceResponse update(Long userId, Long deviceId, DeviceUpdateRequest request) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND));
        accessValidator.requireRelationship(userId, device.getCareTargetId());
        device.update(request.room(), request.positionLabel(), request.nodeRole(), request.status());
        return DeviceResponse.from(device);
    }

    // ── D4: 노드 삭제 ─────────────────────────────────────────────────────────
    @Transactional
    public void delete(Long userId, Long deviceId) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND));
        accessValidator.requireRelationship(userId, device.getCareTargetId());
        deviceRepository.delete(device);
    }

    // ── I4: 헬스체크 (기존 유지) ──────────────────────────────────────────────
    @Transactional
    public void heartbeat(String deviceUid) {
        deviceRepository.findByDeviceUid(deviceUid)
                .orElseThrow(() -> new BusinessException(DeviceErrorCode.DEVICE_NOT_FOUND))
                .recordHeartbeat(Instant.now());
    }
}
