package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.ResolutionType;

import java.time.Instant;

/** E1 — 에스컬레이션 목록 항목 */
public record EscalationSummaryResponse(
        Long escalationId,
        EscalationStatus status,
        ResolutionType resolutionType,
        String summary,
        Instant startedAt,
        Instant resolvedAt
) {
    public static EscalationSummaryResponse from(Escalation e) {
        return new EscalationSummaryResponse(
                e.getId(),
                e.getStatus(),
                e.getResolutionType(),
                e.getSummary(),
                e.getStartedAt(),
                e.getResolvedAt()
        );
    }
}
