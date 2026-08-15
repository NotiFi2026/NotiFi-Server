package com.notifi.server.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tb_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "escalation_step_id")
    private Long escalationStepId;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "care_target_id", nullable = false)
    private Long careTargetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private NotificationCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Notification create(Long escalationStepId, Long recipientUserId, Long careTargetId,
                                      NotificationChannel channel, NotificationCategory category,
                                      String title, String body) {
        Notification n = new Notification();
        n.escalationStepId = escalationStepId;
        n.recipientUserId = recipientUserId;
        n.careTargetId = careTargetId;
        n.channel = channel;
        n.category = category;
        n.title = title;
        n.body = body;
        n.status = NotificationStatus.QUEUED;
        return n;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    /**
     * 읽음은 {@code readAt}에만 기록한다 — {@code status}는 발송 결과(SENT/FAILED)의 자리다.
     *
     * <p>여기서 status를 READ로 덮으면 <b>전달 여부가 사라진다.</b> 응급 알림이 FCM 발송에
     * 실패했더라도 보호자가 앱에서 그 알림을 한 번 열면 FAILED가 READ가 되고, 사후에
     * "이 알림이 실제로 폰에 도달했나"를 되짚을 수 없다. 119까지 이어지는 흐름이라
     * 그 기록은 남아야 한다.
     *
     * <p>읽음 여부 판정은 전부 {@code readAt} 기준이다(N1 unread 필터·{@code is_read} 파생).
     * {@link NotificationStatus#READ}는 이 변경 이전에 쌓인 행이 갖고 있어 삭제하지 않는다.
     */
    public void markRead() {
        this.readAt = Instant.now();
    }
}
