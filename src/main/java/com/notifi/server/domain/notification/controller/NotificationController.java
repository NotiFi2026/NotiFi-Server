package com.notifi.server.domain.notification.controller;

import com.notifi.server.domain.notification.dto.NotificationResponse;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.service.NotificationQueryService;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification", description = "알림 수신 및 FCM 토큰 관리")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @Operation(
            summary = "[N1] 내 알림 목록",
            description = "로그인 유저가 수신한 알림 목록을 최신순으로 반환한다. " +
                          "category(EMERGENCY/DAILY_REPORT/SYSTEM)·unread_only 필터 지원. (권한: 인증)"
    )
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(name = "unread_only", defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 무효 정렬 프로퍼티로 인한 500 방지 — ORDER BY는 쿼리에 created_at DESC 고정
        Pageable safe = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return ApiResponse.success(notificationQueryService.getNotifications(userId, category, unreadOnly, safe));
    }

    @Operation(
            summary = "[N2] 알림 읽음 처리",
            description = "알림을 읽음 상태로 변경한다. 이미 읽은 알림은 멱등 처리(200). (권한: 인증·본인)"
    )
    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        notificationQueryService.markRead(userId, id);
        return ApiResponse.ok();
    }
}
