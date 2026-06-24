package com.notifi.server.domain.auth.dto;

import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;

public record SignupResponse(Long userId, String name, Role role) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getName(), user.getRole());
    }
}
