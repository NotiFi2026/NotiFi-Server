package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.RelationshipType;
import jakarta.validation.constraints.Min;

public record RelationshipUpdateRequest(
        RelationshipType relationshipType,
        @Min(1) Short notifyPriority
) {}
