package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.dto.EscalationStepRequest.GuardianMessage;
import com.notifi.server.domain.escalation.event.GuardianNotifyRequestedEvent;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.notification.entity.Notification;
import com.notifi.server.domain.notification.entity.NotificationCategory;
import com.notifi.server.domain.notification.entity.NotificationChannel;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.notification.repository.NotificationRepository;
import com.notifi.server.domain.report.event.DailyReportSavedEvent;
import com.notifi.server.global.alert.AlertNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String VOICE_CHECK_TITLE = "안부 확인";
    private static final String VOICE_CHECK_BODY = "괜찮으신지 확인이 필요해요. 화면을 눌러 응답해 주세요.";
    private static final String DAILY_REPORT_TITLE = "일일 리포트가 도착했어요";
    private static final String DAILY_REPORT_FALLBACK_BODY = "어제 하루 요약을 확인해 보세요.";

    private final CareRelationshipRepository careRelationshipRepository;
    private final CareTargetRepository careTargetRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationRepository notificationRepository;
    private final FcmSender fcmSender;
    private final AlertNotifier alertNotifier;

    /**
     * 신규 GUARDIAN_NOTIFY 단계 커밋 이후 실행.
     * careTargetId에 연결된 모든 보호자에게 FCM 푸시 발송 + tb_notification 기록.
     * data 페이로드로 앱이 알림 탭 시 응급 화면(escalation_id)으로 딥링크한다.
     * AFTER_COMMIT 발송 — 롤백 시 유령 푸시 방지 + 에스컬레이션 행 잠금 밖에서 네트워크 호출.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchGuardianNotify(GuardianNotifyRequestedEvent event) {
        Long escalationStepId = event.escalationStepId();
        Long escalationId = event.escalationId();
        Long careTargetId = event.careTargetId();
        GuardianMessage guardianMessage = event.guardianMessage();

        Map<Long, List<FcmToken>> tokensByGuardian = resolveGuardianTokens(careTargetId, "알림 발송");
        if (tokensByGuardian == null) {
            return;
        }

        String body = buildBody(guardianMessage);
        Map<String, String> data = Map.of(
                "type", "GUARDIAN_NOTIFY",
                "escalation_id", String.valueOf(escalationId),
                "escalation_step_id", String.valueOf(escalationStepId),
                "care_target_id", String.valueOf(careTargetId)
        );

        tokensByGuardian.forEach((userId, tokens) -> {
            Notification notification = Notification.create(
                    escalationStepId, userId, careTargetId,
                    NotificationChannel.FCM_PUSH, NotificationCategory.EMERGENCY,
                    guardianMessage.title(), body
            );
            sendAndSave(notification, tokens, guardianMessage.title(), body, data,
                    FcmSender.Channel.EMERGENCY);
        });
    }

    /**
     * careTargetId에 연결된 보호자별 FCM 토큰. 보호자가 하나도 없으면 null을 반환한다.
     *
     * <p>{@code findGuardiansByCareTargetId}가 careTarget을 조인하므로 soft-deleted 노인은
     * 여기서 걸러진다 — 삭제된 노인에 대해 알림이 나가지 않는 성질이 이 메서드에 걸려 있다.
     * 반환 Map은 보호자 순서(notify_priority)를 유지한다.
     */
    private Map<Long, List<FcmToken>> resolveGuardianTokens(Long careTargetId, String context) {
        List<Long> guardianUserIds = careRelationshipRepository
                .findGuardiansByCareTargetId(careTargetId)
                .stream()
                .map(CareRelationship::getUserId)
                .toList();

        if (guardianUserIds.isEmpty()) {
            log.warn("[FCM] careTargetId={} 에 연결된 보호자 없음 — {} 건너뜀", careTargetId, context);
            return null;
        }

        Map<Long, List<FcmToken>> tokensByUser = fcmTokenRepository
                .findByUserIdIn(guardianUserIds)
                .stream()
                .collect(Collectors.groupingBy(FcmToken::getUserId));

        // 토큰이 없는 보호자도 빠지면 안 된다 — 알림 기록(tb_notification)은 남겨야 한다
        Map<Long, List<FcmToken>> ordered = new LinkedHashMap<>();
        for (Long userId : guardianUserIds) {
            ordered.put(userId, tokensByUser.getOrDefault(userId, List.of()));
        }
        return ordered;
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

        sendAndSave(notification, tokens, VOICE_CHECK_TITLE, VOICE_CHECK_BODY, data,
                FcmSender.Channel.EMERGENCY);
    }

    /**
     * 신규 일일 리포트 적재 커밋 이후 실행 — 보호자 전원에게 리포트 도착을 알린다.
     * 재적재(UPSERT 갱신)에서는 이벤트가 발행되지 않으므로 AI 재시도로 폰이 여러 번 울지 않는다.
     * 에스컬레이션이 아니라 escalation_step_id는 null이다(tb_notification에서 nullable).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchDailyReport(DailyReportSavedEvent event) {
        Long careTargetId = event.careTargetId();

        Map<Long, List<FcmToken>> tokensByGuardian = resolveGuardianTokens(careTargetId, "리포트 알림");
        if (tokensByGuardian == null) {
            return;
        }

        String title = DAILY_REPORT_TITLE;
        String body = event.headline() != null && !event.headline().isBlank()
                ? event.headline()
                : DAILY_REPORT_FALLBACK_BODY;
        Map<String, String> data = Map.of(
                "type", "DAILY_REPORT",
                "daily_report_id", String.valueOf(event.dailyReportId()),
                "care_target_id", String.valueOf(careTargetId),
                "report_date", String.valueOf(event.reportDate())
        );

        tokensByGuardian.forEach((userId, tokens) -> {
            Notification notification = Notification.create(
                    null, userId, careTargetId,
                    NotificationChannel.FCM_PUSH, NotificationCategory.DAILY_REPORT,
                    title, body
            );
            // 응급이 아니다 — 매일 오는 알림을 응급 채널로 보내면 보호자가 응급 채널을 꺼버린다
            sendAndSave(notification, tokens, title, body, data, FcmSender.Channel.NORMAL);
        });
    }

    /**
     * 모든 토큰에 발송 후 SENT/FAILED 마킹하고 tb_notification에 1건 저장.
     * anyMatch 단락 방지 — 다기기 등록 시 모든 토큰에 발송한다.
     */
    private void sendAndSave(Notification notification, List<FcmToken> tokens,
                             String title, String body, Map<String, String> data,
                             FcmSender.Channel channel) {
        boolean anySent = false;
        for (FcmToken token : tokens) {
            if (fcmSender.send(token.getToken(), title, body, data, channel)) {
                anySent = true;
            }
        }

        if (anySent) {
            notification.markSent();
        } else {
            notification.markFailed();
            log.warn("[FCM] userId={} FCM 발송 실패 (토큰 {}개 중 전부 실패 또는 미등록)",
                    notification.getRecipientUserId(), tokens.size());
            alertOnEmergencyDeliveryFailure(notification, tokens.size());
        }

        notificationRepository.save(notification);
    }

    /**
     * 응급 알림이 한 대도 도달하지 못했으면 사람에게 알린다.
     *
     * <p>낙상이 감지되고 에스컬레이션까지 돌았는데 보호자 폰이 울리지 않은 상황이다.
     * 로그 WARN 한 줄로 두면 아무도 모른 채 지나간다.
     *
     * <p>두 가지로 좁힌다 — 넓히면 알림이 노이즈가 되어 정작 볼 때 안 본다.
     * <ul>
     *   <li><b>EMERGENCY만</b>: 일일 리포트가 못 간 건 다음 날 보면 된다
     *   <li><b>토큰이 있었는데 전부 실패한 경우만</b>: 토큰 미등록은 장애가 아니라
     *       앱 설치·권한 문제이고, 데모·개발 환경에서 상시 발생한다
     * </ul>
     */
    private void alertOnEmergencyDeliveryFailure(Notification notification, int tokenCount) {
        if (notification.getCategory() != NotificationCategory.EMERGENCY || tokenCount == 0) {
            return;
        }
        alertNotifier.critical(
                "응급 알림이 보호자에게 도달하지 못했습니다 (FCM 발송 전량 실패)",
                Map.of(
                        "care_target_id", notification.getCareTargetId(),
                        "recipient_user_id", notification.getRecipientUserId(),
                        "token_count", tokenCount
                )
        );
    }

    private String buildBody(GuardianMessage msg) {
        if (msg.recommendation() != null && !msg.recommendation().isBlank()) {
            return msg.body() + " " + msg.recommendation();
        }
        return msg.body();
    }
}
