package com.notifi.server.domain.escalation.dto;

import com.notifi.server.domain.escalation.entity.ResolutionType;
import jakarta.validation.constraints.NotNull;

/** E3 — 보호자 확인·해제 요청 */
public record EscalationResolveRequest(

        @NotNull
        ResolutionType resolutionType,

        String memo
) {
}
