package com.notifi.server.domain.notification.dto;

import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;

import java.time.Instant;

/**
 * N1 알림 카드.
 *
 * <p>{@code escalationId}는 이 테이블에 없는 값이다 — 알림은 에스컬레이션 <b>단계</b>가
 * 만들어내므로 {@code tb_notification}은 step만 FK로 갖는다(db-spec 3.9). 하지만 앱은
 * 알림함에서 응급 알림을 탭했을 때 {@code emergency/{id}}로 보내야 하므로 step으로는 부족하다.
 * 그래서 조회 시 step을 조인해 채운다 — 컬럼을 늘리지 않고 읽기 모델에서만 해결한다.
 *
 * <p>응급이 아닌 알림(리포트·시스템)은 step 자체가 없어 둘 다 null이다.
 */
public record NotificationResponse(
        Long notificationId,
        NotificationCategory category,
        String title,
        String body,
        boolean isRead,
        Instant readAt,
        Instant createdAt,
        Long careTargetId,
        Long escalationStepId,
        Long escalationId
) {
    public static NotificationResponse from(Notification n, Long escalationId) {
        return new NotificationResponse(
                n.getId(),
                n.getCategory(),
                n.getTitle(),
                n.getBody(),
                n.getReadAt() != null,   // is_read: readAt 단일 출처
                n.getReadAt(),
                n.getCreatedAt(),
                n.getCareTargetId(),
                n.getEscalationStepId(),
                escalationId
        );
    }
}
