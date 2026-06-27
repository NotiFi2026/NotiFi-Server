package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.CareRelationship;

import java.time.Instant;

public record CareTargetSummaryResponse(
        Long careTargetId,
        String name,
        String currentRiskLevel,  // TODO: Sensing 도메인 구현 후 채움
        Instant lastEventAt,      // TODO: Sensing 도메인 구현 후 채움
        int deviceCount,
        boolean isPrimary
) {
    public static CareTargetSummaryResponse from(CareRelationship cr) {
        return from(cr, 0);
    }

    public static CareTargetSummaryResponse from(CareRelationship cr, int deviceCount) {
        return new CareTargetSummaryResponse(
                cr.getCareTarget().getId(),
                cr.getCareTarget().getName(),
                null,
                null,
                deviceCount,
                cr.isPrimary()
        );
    }
}
