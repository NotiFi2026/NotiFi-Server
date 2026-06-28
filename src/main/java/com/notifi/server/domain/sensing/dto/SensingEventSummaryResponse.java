package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;

import java.math.BigDecimal;
import java.time.Instant;

public record SensingEventSummaryResponse(
        Long sensingEventId,
        EventType eventType,
        BigDecimal riskProbability,
        Short riskScore,
        RiskLevel riskLevel,
        Instant detectedAt,
        boolean hasReplay // I5로 포즈클립 적재 시 true
) {
    public static SensingEventSummaryResponse of(SensingEvent e, RiskAssessment ra, boolean hasReplay) {
        return new SensingEventSummaryResponse(
                e.getId(),
                e.getEventType(),
                e.getRiskProbability(),
                ra != null ? ra.getRiskScore() : null,
                ra != null ? ra.getRiskLevel() : null,
                e.getDetectedAt(),
                hasReplay
        );
    }
}
