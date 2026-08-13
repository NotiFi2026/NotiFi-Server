package com.notifi.server.domain.escalation.controller;

import com.notifi.server.domain.escalation.dto.EscalationDetailResponse;
import com.notifi.server.domain.escalation.dto.EscalationResolveRequest;
import com.notifi.server.domain.escalation.dto.EscalationSummaryResponse;
import com.notifi.server.domain.escalation.service.EscalationService;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Escalation", description = "응급 대응 에스컬레이션 조회·해제 (보호자용)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EscalationController {

    private final EscalationService escalationService;

    @Operation(
            summary = "[E1] 에스컬레이션 목록",
            description = "해당 노인에 대해 발생한 에스컬레이션 목록을 최신순으로 페이지 반환한다. (권한: 관계)"
    )
    @GetMapping("/care-targets/{id}/escalations")
    public ApiResponse<PageResponse<EscalationSummaryResponse>> listEscalations(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 정렬은 쿼리에 고정(startedAt DESC) — 외부 sort 파라미터 무시
        Pageable safe = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ApiResponse.success(escalationService.listEscalations(userId, id, safe));
    }

    @Operation(
            summary = "[E2] 에스컬레이션 상세",
            description = "단일 에스컬레이션과 단계별 진행 로그(step_order 오름차순)를 반환한다. (권한: 관계)"
    )
    @GetMapping("/escalations/{id}")
    public ApiResponse<EscalationDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(escalationService.getDetail(userId, id));
    }

    @Operation(
            summary = "[E3] 보호자 확인·해제",
            description = "보호자가 '확인 완료'를 눌러 에스컬레이션을 해제한다. "
                    + "이후 119 자동 신고 단계로 진행되지 않는다. "
                    + "resolution_type은 GUARDIAN_HANDLED 또는 FALSE_ALARM만 허용. "
                    + "이미 종료된 에스컬레이션 재요청 시 409. (권한: 관계)"
    )
    @PostMapping("/escalations/{id}/resolve")
    public ApiResponse<EscalationDetailResponse> resolve(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody EscalationResolveRequest request
    ) {
        return ApiResponse.success(escalationService.resolve(userId, id, request));
    }

    @Operation(
            summary = "[E4] 노인 본인 '괜찮아요' 응답",
            description = "노인이 앱에서 직접 안전을 알려 에스컬레이션을 해소한다. 음성 확인이 어려울 때의 대안 경로로, "
                    + "음성으로 USER_OK를 응답한 것과 같은 SELF_RESOLVED 결과가 된다. "
                    + "보호자는 호출할 수 없다 — 보호자에겐 E3가 따로 있다. "
                    + "이미 종료된 에스컬레이션 재요청 시 409. (권한: 노인 본인)"
    )
    @PostMapping("/escalations/{id}/self-ok")
    public ApiResponse<EscalationDetailResponse> selfConfirmSafe(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(escalationService.selfConfirmSafe(userId, id));
    }
}
