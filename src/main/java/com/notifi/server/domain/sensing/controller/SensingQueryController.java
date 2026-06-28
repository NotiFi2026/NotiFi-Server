package com.notifi.server.domain.sensing.controller;

import com.notifi.server.domain.sensing.dto.CareTargetStatusResponse;
import com.notifi.server.domain.sensing.dto.SensingEventSummaryResponse;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.service.SensingQueryService;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Status", description = "노인 실시간 상태 및 감지 이벤트 조회")
@RestController
@RequestMapping("/api/v1/care-targets")
@RequiredArgsConstructor
public class SensingQueryController {

    private final SensingQueryService sensingQueryService;

    @Operation(summary = "[S1] 실시간 상태 대시보드",
               description = "앱 메인 화면용. 현재 위험도, 최근 활동, 노드 상태를 한 번에 반환한다. " +
                             "today_metrics·active_escalation은 미구현 도메인 의존으로 null 반환. (권한: 관계)")
    @GetMapping("/{id}/status")
    public ApiResponse<CareTargetStatusResponse> getStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(sensingQueryService.getStatus(userId, id));
    }

    @Operation(summary = "[S2] 감지 이벤트 목록",
               description = "낙상·호흡이상·무활동 등 감지 이벤트를 페이지 단위로 반환한다. " +
                             "event_type·from·to 필터 지원. has_replay는 I5로 포즈클립 적재 시 true. (권한: 관계)")
    @GetMapping("/{id}/events")
    public ApiResponse<PageResponse<SensingEventSummaryResponse>> getEvents(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(name = "event_type", required = false) EventType eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 무효 정렬 프로퍼티로 인한 500 방지 — 서버가 detected_at DESC 고정
        Pageable safe = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "detectedAt"));
        return ApiResponse.success(sensingQueryService.getEvents(userId, id, eventType, from, to, safe));
    }
}
