package com.notifi.server.domain.device.controller;

import com.notifi.server.domain.device.dto.DeviceCreateRequest;
import com.notifi.server.domain.device.dto.DeviceCreateResponse;
import com.notifi.server.domain.device.dto.DeviceResponse;
import com.notifi.server.domain.device.dto.DeviceUpdateRequest;
import com.notifi.server.domain.device.service.DeviceService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Device", description = "ESP32 센싱 노드 등록·관리")
@RestController
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(
            summary = "[D1] 노드 등록",
            description = "노인 가구에 설치한 ESP32 노드를 등록한다. device_uid는 ESP32 MAC 주소. 이미 등록된 device_uid는 409. (권한: 관계)"
    )
    @PostMapping("/api/v1/care-targets/{careTargetId}/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long careTargetId,
            @Valid @RequestBody DeviceCreateRequest request
    ) {
        return ApiResponse.success(deviceService.register(userId, careTargetId, request));
    }

    @Operation(
            summary = "[D2] 노드 목록 조회",
            description = "해당 노인 가구의 ESP32 노드 목록을 반환한다. 등록 순 정렬. last_seen_at으로 생존 여부 확인 가능. (권한: 관계)"
    )
    @GetMapping("/api/v1/care-targets/{careTargetId}/devices")
    public ApiResponse<List<DeviceResponse>> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long careTargetId
    ) {
        return ApiResponse.success(deviceService.list(userId, careTargetId));
    }

    @Operation(
            summary = "[D3] 노드 정보 수정",
            description = "노드의 설치 공간(room), 위치 설명(position_label), 역할(node_role), 상태(status)를 수정한다. null 필드는 변경하지 않는다. (권한: 관계)"
    )
    @PatchMapping("/api/v1/devices/{deviceId}")
    public ApiResponse<DeviceResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId,
            @Valid @RequestBody DeviceUpdateRequest request
    ) {
        return ApiResponse.success(deviceService.update(userId, deviceId, request));
    }

    @Operation(
            summary = "[D4] 노드 삭제",
            description = "노드를 삭제한다. 삭제된 노드의 device_uid는 재사용 가능. (권한: 관계)"
    )
    @DeleteMapping("/api/v1/devices/{deviceId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId
    ) {
        deviceService.delete(userId, deviceId);
        return ApiResponse.ok();
    }
}
