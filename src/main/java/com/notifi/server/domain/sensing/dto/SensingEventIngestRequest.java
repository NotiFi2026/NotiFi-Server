package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensorStatus;
import jakarta.validation.constraints.Digits;
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
        @Digits(integer = 1, fraction = 3) BigDecimal riskProbability,
        @Digits(integer = 1, fraction = 3) BigDecimal anomalyScore,
        @Digits(integer = 1, fraction = 3) BigDecimal trendScore,
        SensorStatus sensorStatus,
        @NotNull @Size(max = 30) String modelVersion,
        Map<String, Object> features,
        @NotNull Instant detectedAt,
        @NotNull @Min(0) @Max(100) Short riskScore,
        @NotNull RiskLevel riskLevel,
        Map<String, Object> scoreBreakdown
) {}
