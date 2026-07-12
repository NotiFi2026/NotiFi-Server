package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.dto.EscalationStepRequest.GuardianMessage;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.entity.NotificationChannel;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String VOICE_CHECK_TITLE = "안부 확인";
    private static final String VOICE_CHECK_BODY = "괜찮으신지 확인이 필요해요. 화면을 눌러 응답해 주세요.";

    private final CareRelationshipRepository careRelationshipRepository;
    private final CareTargetRepository careTargetRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final FcmSender fcmSender;

    /**
     * GUARDIAN_NOTIFY 단계 수신 시 호출.
     * careTargetId에 연결된 모든 보호자에게 FCM 푸시 발송 + tb_notification 기록.
     */
    @Transactional
    public void dispatchGuardianNotify(Long escalationStepId, Long careTargetId,
                                       GuardianMessage guardianMessage) {
        List<Long> guardianUserIds = careRelationshipRepository
                .findGuardiansByCareTargetId(careTargetId)
                .stream()
                .map(cr -> cr.getUserId())
                .collect(Collectors.toList());

        if (guardianUserIds.isEmpty()) {
            log.warn("[FCM] careTargetId={} 에 연결된 보호자 없음 — 알림 발송 건너뜀", careTargetId);
            return;
        }

        Map<Long, List<FcmToken>> tokensByUser = fcmTokenRepository
                .findByUserIdIn(guardianUserIds)
                .stream()
                .collect(Collectors.groupingBy(FcmToken::getUserId));

        String body = buildBody(guardianMessage);

        for (Long userId : guardianUserIds) {
            Notification notification = Notification.create(
                    escalationStepId, userId, careTargetId,
                    NotificationChannel.FCM_PUSH, NotificationCategory.EMERGENCY,
                    guardianMessage.title(), body
            );

            List<FcmToken> tokens = tokensByUser.getOrDefault(userId, List.of());
            sendAndSave(notification, tokens, guardianMessage.title(), body, Map.of());
        }
    }

    /**
     * 신규 VOICE_CHECK 진행 단계 커밋 이후 실행.
     * 노인 본인 앱으로 FCM 푸시를 보내 음성확인 UI를 깨운다. 계정 미연결 노인은 건너뜀.
     * 음성 대화 자체는 노인 앱 ↔ AI 서버 직통이며, 여기서는 화면을 깨우는 신호만 보낸다.
     * REQUIRES_NEW 필수 — AFTER_COMMIT 시점엔 원 트랜잭션이 이미 커밋돼 REQUIRED로는 저장이 반영되지 않음.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchVoiceCheck(VoiceCheckRequestedEvent event) {
        Long careTargetId = event.careTargetId();
        Long recipientUserId = careTargetRepository.findById(careTargetId)
                .map(CareTarget::getUserId)
                .orElse(null);
        if (recipientUserId == null) {
            log.info("[FCM] careTargetId={} 노인 계정 미연결 — VOICE_CHECK 푸시 건너뜀", careTargetId);
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findByUserIdIn(List.of(recipientUserId));
        Map<String, String> data = Map.of(
                "type", "VOICE_CHECK",
                "escalation_id", String.valueOf(event.escalationId()),
                "escalation_step_id", String.valueOf(event.escalationStepId())
        );

        Notification notification = Notification.create(
                event.escalationStepId(), recipientUserId, careTargetId,
                NotificationChannel.FCM_PUSH, NotificationCategory.EMERGENCY,
                VOICE_CHECK_TITLE, VOICE_CHECK_BODY
        );

        sendAndSave(notification, tokens, VOICE_CHECK_TITLE, VOICE_CHECK_BODY, data);
    }

    /**
     * 모든 토큰에 발송 후 SENT/FAILED 마킹하고 tb_notification에 1건 저장.
     * anyMatch 단락 방지 — 다기기 등록 시 모든 토큰에 발송한다.
     */
    private void sendAndSave(Notification notification, List<FcmToken> tokens,
                             String title, String body, Map<String, String> data) {
        boolean anySent = false;
        for (FcmToken token : tokens) {
            if (fcmSender.send(token.getToken(), title, body, data)) {
                anySent = true;
            }
        }

        if (anySent) {
            notification.markSent();
        } else {
            notification.markFailed();
            log.warn("[FCM] userId={} FCM 발송 실패 (토큰 {}개 중 전부 실패 또는 미등록)",
                    notification.getRecipientUserId(), tokens.size());
        }

        notificationRepository.save(notification);
    }

    private String buildBody(GuardianMessage msg) {
        if (msg.recommendation() != null && !msg.recommendation().isBlank()) {
            return msg.body() + " " + msg.recommendation();
        }
        return msg.body();
    }
}
