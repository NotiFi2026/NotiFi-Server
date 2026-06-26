package com.notifi.server.domain.caretarget.controller;

import com.notifi.server.domain.caretarget.dto.*;
import com.notifi.server.domain.caretarget.service.RelationshipService;
import com.notifi.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    // R1-a: 초대코드 발급 (주 보호자)
    @PostMapping("/api/v1/care-targets/{id}/invite-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteCodeCreateResponse> issueInviteCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody InviteCodeCreateRequest request
    ) {
        return ApiResponse.success(relationshipService.issueInviteCode(userId, id, request));
    }

    // R1-b: 초대코드 수락 (인증 사용자, 일회성)
    @PostMapping("/api/v1/invite-codes/{code}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteCodeAcceptResponse> acceptInviteCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable String code
    ) {
        return ApiResponse.success(relationshipService.acceptInviteCode(userId, code));
    }

    // R2: 보호자 목록 (관계 보유 사용자)
    @GetMapping("/api/v1/care-targets/{id}/guardians")
    public ApiResponse<List<GuardianResponse>> getGuardians(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(relationshipService.getGuardians(userId, id));
    }

    // R3: 관계 수정 (주 보호자)
    @PatchMapping("/api/v1/relationships/{id}")
    public ApiResponse<RelationshipResponse> updateRelationship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RelationshipUpdateRequest request
    ) {
        return ApiResponse.success(relationshipService.updateRelationship(userId, id, request));
    }

    // R4: 연결 해제 (주 보호자, 주 보호자 본인 차단)
    @DeleteMapping("/api/v1/relationships/{id}")
    public ApiResponse<Void> deleteRelationship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        relationshipService.deleteRelationship(userId, id);
        return ApiResponse.ok();
    }
}
