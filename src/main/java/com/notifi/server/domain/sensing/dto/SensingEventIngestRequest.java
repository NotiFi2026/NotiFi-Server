package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensorStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record SensingEventIngestRequest(
        @NotNull Long careTargetId,
        Long deviceId,
        @NotNull EventType eventType,
        BigDecimal riskProbability,
        BigDecimal anomalyScore,
        BigDecimal trendScore,
        SensorStatus sensorStatus,
        @NotNull @Size(max = 30) String modelVersion,
        Map<String, Object> features,
        @NotNull Instant detectedAt,
        @NotNull @Min(0) @Max(100) Short riskScore,
        @NotNull RiskLevel riskLevel,
        Map<String, Object> scoreBreakdown
) {}
