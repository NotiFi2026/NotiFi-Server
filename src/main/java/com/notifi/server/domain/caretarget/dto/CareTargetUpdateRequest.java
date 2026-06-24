package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.entity.Gender;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CareTargetUpdateRequest(
        @Size(max = 100) String name,
        LocalDate birthDate,
        Gender gender,
        @Size(max = 255) String address,
        String emergencyMemo
) {}
