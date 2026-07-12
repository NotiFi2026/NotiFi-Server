package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.entity.NotificationChannel;
import com.notifi.server.domain.notification.entity.NotificationStatus;
import com.notifi.server.domain.notification.entity.Platform;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock FcmTokenRepository fcmTokenRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock FcmSender fcmSender;

    @InjectMocks NotificationService notificationService;

    // ── dispatchVoiceCheck ────────────────────────────────────────────────

    @Test
    @DisplayName("dispatchVoiceCheck: 노인 계정 미연결 → 발송·저장 모두 건너뜀")
    void dispatchVoiceCheck_unlinkedCareTarget_skips() {
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(careTarget(45L, null)));

        notificationService.dispatchVoiceCheck(new VoiceCheckRequestedEvent(100L, 10L, 45L));

        then(fcmSender).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("dispatchVoiceCheck: 다기기 전체 발송 + VOICE_CHECK data 페이로드 + SENT 저장")
    void dispatchVoiceCheck_success_allTokens() {
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(careTarget(45L, 9L)));
        given(fcmTokenRepository.findByUserIdIn(List.of(9L))).willReturn(List.of(
                FcmToken.create(9L, "token-1", Platform.ANDROID),
                FcmToken.create(9L, "token-2", Platform.IOS)
        ));
        given(fcmSender.send(anyString(), anyString(), anyString(), anyMap())).willReturn(true);

        notificationService.dispatchVoiceCheck(new VoiceCheckRequestedEvent(100L, 10L, 45L));

        Map<String, String> expectedData = Map.of(
                "type", "VOICE_CHECK",
                "escalation_id", "10",
                "escalation_step_id", "100"
        );
        then(fcmSender).should().send(eq("token-1"), anyString(), anyString(), eq(expectedData));
        then(fcmSender).should().send(eq("token-2"), anyString(), anyString(), eq(expectedData));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientUserId()).isEqualTo(9L);
        assertThat(saved.getCareTargetId()).isEqualTo(45L);
        assertThat(saved.getEscalationStepId()).isEqualTo(100L);
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.FCM_PUSH);
        assertThat(saved.getCategory()).isEqualTo(NotificationCategory.EMERGENCY);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("dispatchVoiceCheck: 토큰 전부 실패(또는 미등록) → FAILED로 저장")
    void dispatchVoiceCheck_allTokensFail_markFailed() {
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(careTarget(45L, 9L)));
        given(fcmTokenRepository.findByUserIdIn(List.of(9L))).willReturn(List.of(
                FcmToken.create(9L, "token-1", Platform.ANDROID)
        ));
        given(fcmSender.send(anyString(), anyString(), anyString(), anyMap())).willReturn(false);

        notificationService.dispatchVoiceCheck(new VoiceCheckRequestedEvent(100L, 10L, 45L));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CareTarget careTarget(Long id, Long linkedUserId) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", id);
        if (linkedUserId != null) {
            ct.linkUser(linkedUserId);
        }
        return ct;
    }
}
