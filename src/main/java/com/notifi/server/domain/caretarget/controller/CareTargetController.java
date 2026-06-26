package com.notifi.server.domain.caretarget.controller;

import com.notifi.server.domain.caretarget.service.CareTargetService;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetDetailResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetUpdateRequest;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Care Target", description = "모니터링 대상 노인 관리")
@RestController
@RequestMapping("/api/v1/care-targets")
@RequiredArgsConstructor
public class CareTargetController {

    private final CareTargetService careTargetService;

    @Operation(summary = "[C1] 노인 등록",
               description = "보호자가 모니터링할 노인을 등록한다. 등록자는 자동으로 주 보호자(is_primary=true)로 연결된다. (권한: 인증)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareTargetCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CareTargetCreateRequest request
    ) {
        return ApiResponse.success(careTargetService.register(userId, request));
    }

    @Operation(summary = "[C2] 내가 보는 노인 목록",
               description = "JWT 사용자가 보호자로 연결된 노인 목록을 반환한다. 위험도·디바이스 수 포함. Sensing·Device 도메인 구현 전까지 해당 필드는 null/0. (권한: 인증)")
    @GetMapping
    public ApiResponse<PageResponse<CareTargetSummaryResponse>> getMyCareTargets(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 클라이언트가 보낸 정렬(Swagger 기본 ["string"] 등 무효 프로퍼티 포함)을 신뢰하지 않고
        // 서버가 노인(careTarget) 생성순 고정 정렬로 덮어써 JPQL 정렬 검증 500을 차단
        Pageable safe = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "careTarget.createdAt"));
        return ApiResponse.success(careTargetService.getMyCareTargets(userId, safe));
    }

    @Operation(summary = "[C3] 노인 상세",
               description = "노인 상세 정보(개인정보·응급 메모)를 조회한다. (권한: 관계)")
    @GetMapping("/{id}")
    public ApiResponse<CareTargetDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(careTargetService.getDetail(userId, id));
    }

    @Operation(summary = "[C4] 노인 정보 수정",
               description = "노인의 이름·생년월일·주소·응급 메모 등을 수정한다. null 필드는 변경하지 않는다. (권한: 관계)")
    @PatchMapping("/{id}")
    public ApiResponse<CareTargetDetailResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CareTargetUpdateRequest request
    ) {
        return ApiResponse.success(careTargetService.update(userId, id, request));
    }

    @Operation(summary = "[C5] 노인 삭제",
               description = "노인을 소프트 삭제한다. 주 보호자만 삭제 가능. 삭제 후 목록에서 즉시 제외된다. (권한: 관계 — 주 보호자)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        careTargetService.delete(userId, id);
        return ApiResponse.ok();
    }
}
