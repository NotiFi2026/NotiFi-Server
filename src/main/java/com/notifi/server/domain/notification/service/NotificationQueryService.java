package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.notification.dto.NotificationResponse;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    // ── N1: 내 알림 목록 ──────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(
            Long userId, NotificationCategory category, boolean unreadOnly, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findMyNotifications(userId, category, unreadOnly, pageable)
                        .map(NotificationResponse::from)
        );
    }

    // ── N2: 알림 읽음 처리 ────────────────────────────────────────────────────
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        if (notification.getReadAt() != null) {
            return; // 이미 읽음 — 멱등 no-op
        }

        notification.markRead(); // 더티체킹으로 flush
    }
}
