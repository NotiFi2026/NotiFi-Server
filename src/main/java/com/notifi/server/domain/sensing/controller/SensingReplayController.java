package com.notifi.server.domain.sensing.controller;

import com.notifi.server.domain.sensing.dto.PoseClipResponse;
import com.notifi.server.domain.sensing.service.SensingQueryService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Status", description = "노인 실시간 상태 및 감지 이벤트 조회")
@RestController
@RequestMapping("/api/v1/sensing-events")
@RequiredArgsConstructor
public class SensingReplayController {

    private final SensingQueryService sensingQueryService;

    @Operation(
            summary = "[S3] 복원 스켈레톤 리플레이 조회",
            description = "감지 이벤트에 AI 서버가 복원·적재한 추상 스켈레톤 클립을 반환한다. " +
                          "카메라 영상이 아닌 13-point 좌표 시퀀스(개인정보 없음). " +
                          "클립 미존재(NORMAL 이벤트 등)는 404 반환. (권한: 관계)"
    )
    @GetMapping("/{sensingEventId}/pose-clip")
    public ApiResponse<PoseClipResponse> getPoseClip(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long sensingEventId
    ) {
        return ApiResponse.success(sensingQueryService.getPoseClip(userId, sensingEventId));
    }
}
