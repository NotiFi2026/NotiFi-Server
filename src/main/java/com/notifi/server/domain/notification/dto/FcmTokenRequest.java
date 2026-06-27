package com.notifi.server.domain.notification.dto;

import com.notifi.server.domain.notification.entity.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FcmTokenRequest(
        @NotBlank String fcmToken,
        @NotNull Platform platform
) {}
