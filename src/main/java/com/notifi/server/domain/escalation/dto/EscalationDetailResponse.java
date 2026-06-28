package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.ResolutionType;

import java.time.Instant;
import java.util.List;

/** E2·E3 — 에스컬레이션 상세 + 단계별 진행 로그 */
public record EscalationDetailResponse(
        Long escalationId,
        EscalationStatus status,
        ResolutionType resolutionType,
        String resolutionMemo,
        Instant startedAt,
        Instant resolvedAt,
        List<EscalationStepResponse> steps
) {
    public static EscalationDetailResponse of(Escalation e, List<EscalationStep> steps) {
        return new EscalationDetailResponse(
                e.getId(),
                e.getStatus(),
                e.getResolutionType(),
                e.getResolutionMemo(),
                e.getStartedAt(),
                e.getResolvedAt(),
                steps.stream().map(EscalationStepResponse::from).toList()
        );
    }
}
