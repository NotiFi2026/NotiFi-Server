package com.notifi.server.domain.caretarget.dto;

import com.notifi.server.domain.caretarget.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CareTargetCreateRequest(
        @NotBlank @Size(max = 100) String name,
        LocalDate birthDate,
        Gender gender,
        @Size(max = 255) String address,
        String emergencyMemo
) {}
