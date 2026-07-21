package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepStatus;
import com.notifi.server.domain.escalation.entity.StepType;

import java.time.Instant;

public record EscalationStepResponse(
        Long stepId,
        Long escalationId,
        StepType stepType,
        int stepOrder,
        StepStatus status,
        Instant executedAt,
        Instant respondedAt,
        Instant createdAt,
        EscalationStatus escalationStatus
) {

    public static EscalationStepResponse from(EscalationStep step, EscalationStatus escalationStatus) {
        return new EscalationStepResponse(
                step.getId(),
                step.getEscalationId(),
                step.getStepType(),
                step.getStepOrder(),
                step.getStatus(),
                step.getExecutedAt(),
                step.getRespondedAt(),
                step.getCreatedAt(),
                escalationStatus
        );
    }
}
