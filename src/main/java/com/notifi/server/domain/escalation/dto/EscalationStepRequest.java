package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.StepStatus;
import com.notifi.server.domain.escalation.entity.StepType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record EscalationStepRequest(

        @NotNull
        StepType stepType,

        @NotNull
        @Min(1)
        Integer stepOrder,

        @NotNull
        StepStatus status,

        @NotNull
        Instant executedAt,

        Instant respondedAt,

        Map<String, Object> responseDetail,

        @Valid
        GuardianMessage guardianMessage
) {

    public record GuardianMessage(
            @NotNull String title,
            @NotNull String body,
            String recommendation
    ) {
    }
}
