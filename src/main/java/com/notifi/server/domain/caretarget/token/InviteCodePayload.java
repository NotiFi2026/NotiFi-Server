package com.notifi.server.domain.caretarget.token;

import com.notifi.server.domain.caretarget.entity.RelationshipType;

public record InviteCodePayload(
        Long careTargetId,
        RelationshipType relationshipType,
        short notifyPriority,
        Long issuedBy
) {}
