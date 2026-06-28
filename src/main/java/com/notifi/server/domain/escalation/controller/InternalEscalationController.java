package com.notifi.server.domain.escalation.controller;

import com.notifi.server.domain.escalation.dto.EscalationStepRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepResponse;
import com.notifi.server.domain.escalation.service.EscalationService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal Escalation", description = "AI 서버 → Spring 내부 통신 — 에스컬레이션 단계 기록")
@RestController
@RequestMapping("/internal/v1/escalations")
@RequiredArgsConstructor
public class InternalEscalationController {

    private final EscalationService escalationService;

    @Operation(
            summary = "[I2] 에스컬레이션 단계 진행 기록",
            description = "LangGraph 에이전트가 각 단계(음성확인→보호자알림→119) 실행 결과를 기록한다. "
                    + "GUARDIAN_NOTIFY 단계 + guardian_message 전달 시 Backend가 FCM 발송 + tb_notification 기록을 트리거한다. "
                    + "(escalation_id, step_type) 기준 멱등 처리 — 재요청 시 기존 row를 갱신하고 FCM 재발송하지 않는다. "
                    + "(권한: X-Internal-Key)"
    )
    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EscalationStepResponse> recordStep(
            @PathVariable Long id,
            @Valid @RequestBody EscalationStepRequest request
    ) {
        return ApiResponse.success(escalationService.recordStep(id, request));
    }
}
