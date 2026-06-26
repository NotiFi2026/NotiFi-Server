package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.RelationshipType;

import java.time.Instant;

public record InvitePreviewResponse(
        Long careTargetId,
        String careTargetName,
        String inviterName,
        RelationshipType relationshipType,
        Instant expiresAt
) {}
