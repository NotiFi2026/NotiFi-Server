package com.notifi.server.global.security.jwt;

import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = new JwtTokenProvider(
            "test-secret-key-at-least-32-bytes-long-for-hs256!", 3600, 604800, 7776000);

    @Test
    @DisplayName("액세스 토큰 → getAuthentication 으로 userId·role 추출")
    void accessToken_authenticates() {
        String access = provider.createAccessToken(1L, "GUARDIAN");

        Authentication auth = provider.getAuthentication(access);

        assertThat(auth.getPrincipal()).isEqualTo(1L);
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_GUARDIAN");
    }

    @Test
    @DisplayName("리프레시 토큰으로 API 인증 시도 → INVALID_CREDENTIALS (용도 불일치 거부)")
    void refreshToken_rejectedAsAccessToken() {
        String refresh = provider.createRefreshToken(1L, "GUARDIAN");

        assertThatThrownBy(() -> provider.getAuthentication(refresh))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("리프레시 토큰 → getRefreshTokenUserId 로 userId 추출")
    void refreshToken_returnsUserId() {
        String refresh = provider.createRefreshToken(7L, "SOCIAL_WORKER");

        assertThat(provider.getRefreshTokenUserId(refresh)).isEqualTo(7L);
    }

    @Test
    @DisplayName("액세스 토큰으로 리프레시 시도 → INVALID_CREDENTIALS (용도 불일치 거부)")
    void accessToken_rejectedAsRefreshToken() {
        String access = provider.createAccessToken(1L, "GUARDIAN");

        assertThatThrownBy(() -> provider.getRefreshTokenUserId(access))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("위조 토큰 → INVALID_CREDENTIALS")
    void tamperedToken_rejected() {
        String access = provider.createAccessToken(1L, "GUARDIAN");
        String tampered = access.substring(0, access.length() - 4) + "xxxx";

        assertThatThrownBy(() -> provider.getAuthentication(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("노인 리프레시 수명이 보호자보다 길다 — 끊기면 스스로 못 돌아오기 때문")
    void recipientRefreshLivesLonger() {
        long guardian = provider.refreshTokenTtlFor("GUARDIAN");
        long recipient = provider.refreshTokenTtlFor("CARE_RECIPIENT");

        assertThat(recipient).isGreaterThan(guardian);
    }

    @Test
    @DisplayName("알 수 없는 역할은 보호자 기본값 — 새 역할이 조용히 긴 수명을 얻으면 안 된다")
    void unknownRoleFallsBackToDefault() {
        assertThat(provider.refreshTokenTtlFor("SOCIAL_WORKER"))
                .isEqualTo(provider.refreshTokenTtlFor("GUARDIAN"));
    }
}
