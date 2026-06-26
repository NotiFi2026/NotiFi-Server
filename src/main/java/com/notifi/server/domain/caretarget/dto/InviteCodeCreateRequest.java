package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.RelationshipType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InviteCodeCreateRequest(
        @NotNull RelationshipType relationshipType,
        @Min(1) Short notifyPriority
) {}
