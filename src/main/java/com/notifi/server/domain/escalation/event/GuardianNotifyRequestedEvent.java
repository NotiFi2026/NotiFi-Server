package com.notifi.server.domain.escalation.event;

import com.notifi.server.domain.escalation.dto.EscalationStepRequest.GuardianMessage;

/**
 * 신규 GUARDIAN_NOTIFY 단계 기록 시 발행 — 커밋 이후 보호자 FCM 발송을 트리거한다.
 * 트랜잭션 안에서 직접 발송하지 않으므로 롤백 시 유령 푸시가 없고,
 * 에스컬레이션 행 잠금을 쥔 채 FCM 네트워크 호출을 하지 않는다.
 */
public record GuardianNotifyRequestedEvent(
        Long escalationStepId, Long escalationId, Long careTargetId, GuardianMessage guardianMessage) {
}
