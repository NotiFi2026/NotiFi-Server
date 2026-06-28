package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.escalation.dto.EscalationStepRequest.GuardianMessage;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.entity.NotificationChannel;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final CareRelationshipRepository careRelationshipRepository;
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
            // anyMatch 단락 방지 — 보호자가 다기기 등록 시 모든 토큰에 발송
            boolean anySent = false;
            for (FcmToken token : tokens) {
                if (fcmSender.send(token.getToken(), guardianMessage.title(), body)) {
                    anySent = true;
                }
            }

            if (anySent) {
                notification.markSent();
            } else {
                notification.markFailed();
                log.warn("[FCM] userId={} FCM 발송 실패 (토큰 {}개 중 전부 실패 또는 미등록)",
                        userId, tokens.size());
            }

            notificationRepository.save(notification);
        }
    }

    private String buildBody(GuardianMessage msg) {
        if (msg.recommendation() != null && !msg.recommendation().isBlank()) {
            return msg.body() + " " + msg.recommendation();
        }
        return msg.body();
    }
}
