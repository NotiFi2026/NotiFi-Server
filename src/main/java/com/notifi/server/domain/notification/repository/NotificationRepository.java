package com.notifi.server.domain.notification.repository;

import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // N1: 수신자별 목록 — category·unread_only nullable 필터, created_at DESC 고정
    @Query("SELECT n FROM Notification n WHERE n.recipientUserId = :userId " +
           "AND (:category IS NULL OR n.category = :category) " +
           "AND (:unreadOnly = false OR n.readAt IS NULL) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findMyNotifications(
            @Param("userId") Long userId,
            @Param("category") NotificationCategory category,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);
}
