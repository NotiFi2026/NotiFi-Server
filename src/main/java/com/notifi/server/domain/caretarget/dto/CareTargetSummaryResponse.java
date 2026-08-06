package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;

public record CareTargetSummaryResponse(
        Long careTargetId,
        String name,
        RiskLevel currentRiskLevel,
        Instant lastEventAt,
        int deviceCount,
        boolean isPrimary
) {
    public static CareTargetSummaryResponse from(
            CareRelationship cr, int deviceCount, RiskLevel currentRiskLevel, Instant lastEventAt) {
        return new CareTargetSummaryResponse(
                cr.getCareTarget().getId(),
                cr.getCareTarget().getName(),
                currentRiskLevel,
                lastEventAt,
                deviceCount,
                cr.isPrimary()
        );
    }
}
