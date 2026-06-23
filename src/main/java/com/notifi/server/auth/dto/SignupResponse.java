package com.notifi.server.auth.dto;

import com.notifi.server.user.Role;
import com.notifi.server.user.User;

public record SignupResponse(Long userId, String name, Role role) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getName(), user.getRole());
    }
}
