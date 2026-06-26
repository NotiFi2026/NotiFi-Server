package com.notifi.server.domain.sensing.dto;

public record SensingEventIngestResponse(
        Long sensingEventId,
        Long riskAssessmentId,
        boolean escalationTriggered,
        Long escalationId
) {}
