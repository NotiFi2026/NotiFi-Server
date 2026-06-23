package com.notifi.server.auth.dto;

import com.notifi.server.user.Role;
import com.notifi.server.user.User;

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
