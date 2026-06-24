package com.notifi.server.domain.auth.dto;

import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;

public record LoginResponse(String accessToken, String refreshToken, UserSummary user) {

    public record UserSummary(Long userId, String name, Role role) {}

    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), user.getName(), user.getRole())
        );
    }
}
