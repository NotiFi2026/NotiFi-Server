package com.notifi.server.domain.escalation.event;

/**
 * 신규 VOICE_CHECK 진행 단계 기록 시 발행 — 커밋 이후 노인 앱 FCM 발송을 트리거한다.
 */
public record VoiceCheckRequestedEvent(Long escalationStepId, Long escalationId, Long careTargetId) {
}
