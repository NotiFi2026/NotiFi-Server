package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;
import java.util.List;

public record CareTargetStatusResponse(
        Long careTargetId,
        RiskLevel currentRiskLevel,
        Instant lastActivityAt,
        Object todayMetrics,      // tb_activity_aggregate 미구현 → 항상 null
        List<DeviceStatusItem> devices,
        Object activeEscalation   // E 도메인 보류 → 항상 null
) {}
