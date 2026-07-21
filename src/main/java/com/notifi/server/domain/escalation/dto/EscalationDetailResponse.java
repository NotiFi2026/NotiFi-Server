package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.ResolutionType;
import com.notifi.server.domain.sensing.entity.EventType;

import java.time.Instant;
import java.util.List;

/** E2·E3 — 에스컬레이션 상세 + 단계별 진행 로그 + 노인·이벤트 컨텍스트 */
public record EscalationDetailResponse(
        Long escalationId,
        EscalationStatus status,
        ResolutionType resolutionType,
        String resolutionMemo,
        Instant startedAt,
        Instant resolvedAt,
        Long careTargetId,
        String careTargetName,
        EventType eventType,
        List<EscalationStepResponse> steps
) {
    public static EscalationDetailResponse of(Escalation e, List<EscalationStep> steps,
                                              Long careTargetId, String careTargetName,
                                              EventType eventType) {
        return new EscalationDetailResponse(
                e.getId(),
                e.getStatus(),
                e.getResolutionType(),
                e.getResolutionMemo(),
                e.getStartedAt(),
                e.getResolvedAt(),
                careTargetId,
                careTargetName,
                eventType,
                steps.stream().map(s -> EscalationStepResponse.from(s, e.getStatus())).toList()
        );
    }
}
