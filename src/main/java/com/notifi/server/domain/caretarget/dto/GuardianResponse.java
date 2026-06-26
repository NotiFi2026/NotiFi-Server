package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.RelationshipType;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;

public record GuardianResponse(
        Long relationshipId,
        Long userId,
        String name,
        String email,
        Role role,
        RelationshipType relationshipType,
        boolean isPrimary,
        short notifyPriority
) {
    public static GuardianResponse from(CareRelationship cr, User user) {
        return new GuardianResponse(
                cr.getId(),
                cr.getUserId(),
                user != null ? user.getName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getRole() : null,
                cr.getRelationshipType(),
                cr.isPrimary(),
                cr.getNotifyPriority()
        );
    }
}
