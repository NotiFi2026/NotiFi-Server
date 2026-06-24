package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.CareRelationship;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;

import java.time.Instant;
import java.time.LocalDate;

public record CareTargetDetailResponse(
        Long careTargetId,
        String name,
        LocalDate birthDate,
        Gender gender,
        String address,
        String emergencyMemo,
        boolean isPrimary,
        Instant createdAt
) {
    public static CareTargetDetailResponse from(CareRelationship cr) {
        CareTarget ct = cr.getCareTarget();
        return new CareTargetDetailResponse(
                ct.getId(),
                ct.getName(),
                ct.getBirthDate(),
                ct.getGender(),
                ct.getAddress(),
                ct.getEmergencyMemo(),
                cr.isPrimary(),
                ct.getCreatedAt()
        );
    }
}
