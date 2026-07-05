package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.notification.dto.NotificationResponse;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.entity.NotificationChannel;
import com.notifi.server.domain.notification.entity.NotificationStatus;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationQueryService notificationQueryService;

    // ── N1: getNotifications ─────────────────────────────────────────────────

    @Test
    @DisplayName("getNotifications: 본인 알림 최신순 페이지 반환·DTO 매핑(is_read 도출) 일치")
    void getNotifications_success_returnsMappedPage() {
        Notification n = notification(3L, 1L, 45L, NotificationCategory.EMERGENCY, false);
        given(notificationRepository.findMyNotifications(1L, null, false, Pageable.unpaged()))
                .willReturn(new PageImpl<>(List.of(n)));

        PageResponse<NotificationResponse> result =
                notificationQueryService.getNotifications(1L, null, false, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        NotificationResponse dto = result.content().get(0);
        assertThat(dto.notificationId()).isEqualTo(3L);
        assertThat(dto.category()).isEqualTo(NotificationCategory.EMERGENCY);
        assertThat(dto.careTargetId()).isEqualTo(45L);
        assertThat(dto.isRead()).isFalse();
        assertThat(dto.readAt()).isNull();
    }

    @Test
    @DisplayName("getNotifications: category·unread_only 파라미터가 리포지토리로 전달된다")
    void getNotifications_filtersPassedToRepository() {
        given(notificationRepository.findMyNotifications(
                eq(1L), eq(NotificationCategory.EMERGENCY), eq(true), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));

        notificationQueryService.getNotifications(1L, NotificationCategory.EMERGENCY, true, Pageable.unpaged());

        then(notificationRepository).should()
                .findMyNotifications(eq(1L), eq(NotificationCategory.EMERGENCY), eq(true), any(Pageable.class));
    }

    @Test
    @DisplayName("getNotifications: 이미 읽은 알림 → is_read=true, read_at 존재")
    void getNotifications_readNotification_isReadTrue() {
        Notification n = notification(5L, 1L, 45L, NotificationCategory.SYSTEM, true);
        given(notificationRepository.findMyNotifications(1L, null, false, Pageable.unpaged()))
                .willReturn(new PageImpl<>(List.of(n)));

        NotificationResponse dto = notificationQueryService
                .getNotifications(1L, null, false, Pageable.unpaged())
                .content().get(0);

        assertThat(dto.isRead()).isTrue();
        assertThat(dto.readAt()).isNotNull();
    }

    // ── N2: markRead ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("markRead: 미읽음 → status READ·read_at 설정")
    void markRead_unread_setsStatusAndReadAt() {
        Notification n = notification(10L, 1L, 45L, NotificationCategory.EMERGENCY, false);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        notificationQueryService.markRead(1L, 10L);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markRead: 이미 읽음 → 멱등(status 변경 없음)")
    void markRead_alreadyRead_noOp() {
        Notification n = notification(10L, 1L, 45L, NotificationCategory.EMERGENCY, true);
        NotificationStatus beforeStatus = n.getStatus();
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        notificationQueryService.markRead(1L, 10L);

        // markRead()가 다시 불리지 않았으므로 status 변화 없음
        assertThat(n.getStatus()).isEqualTo(beforeStatus);
    }

    @Test
    @DisplayName("markRead: 없는 알림 → RESOURCE_NOT_FOUND")
    void markRead_notFound() {
        given(notificationRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationQueryService.markRead(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("markRead: 타인 알림 → ACCESS_DENIED")
    void markRead_otherUsersNotification_accessDenied() {
        Notification n = notification(10L, 2L, 45L, NotificationCategory.EMERGENCY, false); // recipient=2L
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationQueryService.markRead(1L, 10L)) // userId=1L
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification notification(Long id, Long recipientUserId, Long careTargetId,
                                      NotificationCategory category, boolean alreadyRead) {
        Notification n = Notification.create(null, recipientUserId, careTargetId,
                NotificationChannel.FCM_PUSH, category, "테스트 알림", "본문");
        n.markSent();
        ReflectionTestUtils.setField(n, "id", id);
        if (alreadyRead) {
            n.markRead();
        }
        return n;
    }
}
