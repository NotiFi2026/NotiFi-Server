package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.escalation.dto.ActiveEscalationSummary;
import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;
import java.util.List;

public record CareTargetStatusResponse(
        Long careTargetId,
        RiskLevel currentRiskLevel,
        Instant lastActivityAt,
        Object todayMetrics,      // tb_activity_aggregate 미구현 → 항상 null
        List<DeviceStatusItem> devices,
        ActiveEscalationSummary activeEscalation   // 진행 중 에스컬레이션 요약 (없으면 null)
) {}
