package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.RelationshipType;

public record RelationshipResponse(
        Long relationshipId,
        Long careTargetId,
        Long userId,
        RelationshipType relationshipType,
        boolean isPrimary,
        short notifyPriority
) {
    public static RelationshipResponse from(CareRelationship cr) {
        return new RelationshipResponse(
                cr.getId(),
                cr.getCareTarget().getId(),
                cr.getUserId(),
                cr.getRelationshipType(),
                cr.isPrimary(),
                cr.getNotifyPriority()
        );
    }
}
