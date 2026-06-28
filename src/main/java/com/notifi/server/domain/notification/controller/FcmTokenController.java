package com.notifi.server.domain.notification.controller;

import com.notifi.server.domain.notification.dto.FcmTokenRequest;
import com.notifi.server.domain.notification.service.FcmTokenService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림 수신 및 FCM 토큰 관리")
@RestController
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenService fcmTokenService;

    @Operation(
            summary = "[N3] FCM 토큰 등록",
            description = "앱 실행 시 디바이스 FCM 토큰을 등록/갱신한다. 같은 토큰 재등록 시 user_id·platform 갱신(멱등). (권한: 인증)"
    )
    @PostMapping("/api/v1/me/fcm-token")
    public ApiResponse<Void> register(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FcmTokenRequest request
    ) {
        fcmTokenService.register(userId, request);
        return ApiResponse.ok();
    }
}
