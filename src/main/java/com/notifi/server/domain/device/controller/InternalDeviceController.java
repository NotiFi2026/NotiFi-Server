package com.notifi.server.domain.device.controller;

import com.notifi.server.domain.device.service.DeviceService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Internal Device", description = "AI 서버 → Spring 내부 통신 — 디바이스 헬스체크")
@RestController
@RequestMapping("/internal/v1/devices")
@RequiredArgsConstructor
public class InternalDeviceController {

    private final DeviceService deviceService;

    @Operation(
            summary = "[I4] 노드 헬스체크",
            description = "ESP32 노드가 주기적으로 생존 신호를 보내면 last_seen_at을 갱신한다. (권한: X-Internal-Key)"
    )
    @PostMapping("/{device_uid}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable("device_uid") String deviceUid) {
        deviceService.heartbeat(deviceUid);
        return ApiResponse.ok();
    }
}
