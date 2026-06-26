package com.notifi.server.domain.caretarget.controller;

import com.notifi.server.domain.caretarget.dto.*;
import com.notifi.server.domain.caretarget.service.RelationshipService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Relationship", description = "노인↔보호자 N:N 연결 관리")
@RestController
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipService relationshipService;

    // R1-a: 초대코드 발급 (주 보호자)
    @Operation(summary = "[R1-a] 초대코드 발급",
               description = "주 보호자가 다른 사람을 보호자로 초대하는 8자리 코드와 공유 링크(invite_url)를 발급한다. 코드는 24시간 유효하며 일회성이다. (권한: 관계 — 주 보호자)")
    @PostMapping("/api/v1/care-targets/{id}/invite-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteCodeCreateResponse> issueInviteCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody InviteCodeCreateRequest request
    ) {
        return ApiResponse.success(relationshipService.issueInviteCode(userId, id, request));
    }

    // R1-c: 초대코드 미리보기 (코드 유지 — 수락 다이얼로그 정보 제공)
    @Operation(summary = "[R1-c] 초대코드 미리보기",
               description = "초대 링크를 클릭한 사용자에게 노인 이름·초대자·관계 유형을 반환한다. 코드를 소모하지 않으므로 수락 전 다이얼로그 표시에 사용한다. (권한: 인증)")
    @GetMapping("/api/v1/invite-codes/{code}")
    public ApiResponse<InvitePreviewResponse> previewInviteCode(
            @PathVariable String code
    ) {
        return ApiResponse.success(relationshipService.previewInviteCode(code));
    }

    // R1-b: 초대코드 수락 (인증 사용자, 일회성)
    @Operation(summary = "[R1-b] 초대코드 수락",
               description = "초대 코드를 입력해 해당 노인의 보호자로 자가 연결한다. 코드는 한 번 사용 후 즉시 소멸된다(동시 수락 불가). (권한: 인증)")
    @PostMapping("/api/v1/invite-codes/{code}/accept")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteCodeAcceptResponse> acceptInviteCode(
            @AuthenticationPrincipal Long userId,
            @PathVariable String code
    ) {
        return ApiResponse.success(relationshipService.acceptInviteCode(userId, code));
    }

    // R2: 보호자 목록 (관계 보유 사용자)
    @Operation(summary = "[R2] 보호자 목록",
               description = "해당 노인의 보호자 전체 목록을 notify_priority 오름차순으로 반환한다. (권한: 관계)")
    @GetMapping("/api/v1/care-targets/{id}/guardians")
    public ApiResponse<List<GuardianResponse>> getGuardians(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(relationshipService.getGuardians(userId, id));
    }

    // R3: 관계 수정 (주 보호자)
    @Operation(summary = "[R3] 관계 수정",
               description = "주 보호자가 다른 보호자의 관계 유형·알림 우선순위를 수정한다. is_primary 변경은 지원하지 않는다. (권한: 관계 — 주 보호자)")
    @PatchMapping("/api/v1/relationships/{id}")
    public ApiResponse<RelationshipResponse> updateRelationship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody RelationshipUpdateRequest request
    ) {
        return ApiResponse.success(relationshipService.updateRelationship(userId, id, request));
    }

    // R4: 연결 해제 (주 보호자, 주 보호자 본인 차단)
    @Operation(summary = "[R4] 연결 해제",
               description = "주 보호자가 다른 보호자의 연결을 해제한다. 주 보호자 본인 연결은 409 CANNOT_DELETE_PRIMARY로 차단된다. (권한: 관계 — 주 보호자)")
    @DeleteMapping("/api/v1/relationships/{id}")
    public ApiResponse<Void> deleteRelationship(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        relationshipService.deleteRelationship(userId, id);
        return ApiResponse.ok();
    }
}
