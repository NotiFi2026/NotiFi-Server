package com.notifi.server.domain.caretarget.controller;

import com.notifi.server.domain.caretarget.service.CareTargetService;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateRequest;
import com.notifi.server.domain.caretarget.dto.CareTargetCreateResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetDetailResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetSummaryResponse;
import com.notifi.server.domain.caretarget.dto.CareTargetUpdateRequest;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care-targets")
@RequiredArgsConstructor
public class CareTargetController {

    private final CareTargetService careTargetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CareTargetCreateResponse> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CareTargetCreateRequest request
    ) {
        return ApiResponse.success(careTargetService.register(userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<CareTargetSummaryResponse>> getMyCareTargets(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 클라이언트가 보낸 정렬(Swagger 기본 ["string"] 등 무효 프로퍼티 포함)을 신뢰하지 않고
        // 서버가 안전한 고정 정렬(createdAt desc)로 덮어써 JPQL 정렬 검증 500을 차단
        Pageable safe = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(careTargetService.getMyCareTargets(userId, safe));
    }

    @GetMapping("/{id}")
    public ApiResponse<CareTargetDetailResponse> getDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(careTargetService.getDetail(userId, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CareTargetDetailResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CareTargetUpdateRequest request
    ) {
        return ApiResponse.success(careTargetService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        careTargetService.delete(userId, id);
        return ApiResponse.ok();
    }
}
