package com.notifi.server.domain.notification.dto;

import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;

import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        NotificationCategory category,
        String title,
        String body,
        boolean isRead,
        Instant readAt,
        Instant createdAt,
        Long careTargetId,
        Long escalationStepId
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getCategory(),
                n.getTitle(),
                n.getBody(),
                n.getReadAt() != null,   // is_read: readAt 단일 출처
                n.getReadAt(),
                n.getCreatedAt(),
                n.getCareTargetId(),
                n.getEscalationStepId()
        );
    }
}
