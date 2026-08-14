package com.notifi.server.domain.notification.repository;

import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * N1: 수신자별 목록 — category·unread_only nullable 필터, created_at DESC 고정.
     *
     * <p>{@code [Notification, escalationId]} 쌍을 준다. tb_notification은 step만 FK로 갖는데
     * 앱은 응급 알림 탭 시 에스컬레이션으로 딥링크해야 해서 step을 조인해 한 번에 끌어온다
     * (알림마다 따로 조회하면 N+1이다). 응급이 아닌 알림은 step이 null이라 조인 결과도 null이다.
     *
     * <p>엔티티를 그대로 넘기는 건 의도다 — 생성자 프로젝션으로 바꾸면 {@code is_read}를
     * {@code readAt != null}로 파생시키는 규칙이 JPQL로 새고, 파라미터 순서가 깨지기 쉬운
     * 계약이 된다. 매핑은 {@code NotificationResponse.from()} 한 곳에 둔다.
     */
    @Query("SELECT n, s.escalationId FROM Notification n " +
           "LEFT JOIN EscalationStep s ON s.id = n.escalationStepId " +
           "WHERE n.recipientUserId = :userId " +
           "AND (:category IS NULL OR n.category = :category) " +
           "AND (:unreadOnly = false OR n.readAt IS NULL) " +
           "ORDER BY n.createdAt DESC")
    Page<Object[]> findMyNotifications(
            @Param("userId") Long userId,
            @Param("category") NotificationCategory category,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);
}
