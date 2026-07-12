package com.notifi.server.domain.auth.dto;

import com.notifi.server.domain.user.entity.User;

/** 노인 가입 응답 — 즉시 로그인 상태로 진입하며, 이후 본인 상태 조회에 쓸 care_target_id를 함께 준다. */
public record RecipientSignupResponse(
        String accessToken,
        String refreshToken,
        LoginResponse.UserSummary user,
        Long careTargetId
) {
    public static RecipientSignupResponse of(String accessToken, String refreshToken,
                                             User user, Long careTargetId) {
        return new RecipientSignupResponse(
                accessToken,
                refreshToken,
                new LoginResponse.UserSummary(user.getId(), user.getName(), user.getRole()),
                careTargetId
        );
    }
}
