package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepType;

import java.time.Instant;

/** S1 — 대시보드에 노출하는 진행 중 에스컬레이션 요약 */
public record ActiveEscalationSummary(
        Long escalationId,
        Instant startedAt,
        StepType currentStepType
) {
    public static ActiveEscalationSummary of(Escalation escalation, EscalationStep latestStep) {
        return new ActiveEscalationSummary(
                escalation.getId(),
                escalation.getStartedAt(),
                latestStep != null ? latestStep.getStepType() : null
        );
    }
}
