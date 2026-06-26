package com.notifi.server.domain.sensing.controller;

import com.notifi.server.domain.sensing.dto.SensingEventIngestRequest;
import com.notifi.server.domain.sensing.dto.SensingEventIngestResponse;
import com.notifi.server.domain.sensing.service.SensingService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal Sensing", description = "AI 서버 → Spring 내부 통신 — 센싱 이벤트 적재")
@RestController
@RequestMapping("/internal/v1/sensing-events")
@RequiredArgsConstructor
public class InternalSensingController {

    private final SensingService sensingService;

    @Operation(
            summary = "[I1] 센싱 이벤트 적재",
            description = "AI 서버가 판정한 감지 이벤트·위험도를 적재한다. risk_level=DANGER 시 에스컬레이션을 자동 생성하고 escalation_id를 반환한다. (care_target_id, detected_at, event_type) 기준 멱등 처리. (권한: X-Internal-Key)"
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SensingEventIngestResponse> ingest(
            @Valid @RequestBody SensingEventIngestRequest request
    ) {
        return ApiResponse.success(sensingService.ingest(request));
    }
}
