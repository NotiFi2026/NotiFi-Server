package com.notifi.server.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 노인 본인 가입 요청 — role은 서버가 CARE_RECIPIENT로 고정하므로 받지 않는다. */
public record RecipientSignupRequest(
        @NotBlank String code,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String name
) {}
