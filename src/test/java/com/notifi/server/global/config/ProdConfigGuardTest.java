package com.notifi.server.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProdConfigGuardTest {

    private static final String VALID_JWT = "prod-secret-key-with-enough-entropy-1234567890ab";
    private static final String VALID_KEY = "prod-internal-key-1234567890";
    private static final String VALID_CORS = "https://app.bloom-safety.app";

    @Test
    @DisplayName("정상 값이면 부팅 통과")
    void validConfig_passes() {
        assertThatCode(() -> new ProdConfigGuard(VALID_JWT, VALID_KEY, VALID_CORS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev 기본 JWT_SECRET → 부팅 실패")
    void devJwtSecret_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(
                "notifi-secret-key-for-local-dev-only-change-in-prod!", VALID_KEY, VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName(".env.example 예시 JWT_SECRET → 부팅 실패")
    void exampleJwtSecret_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(
                "your-secret-key-must-be-at-least-32-characters-long!", VALID_KEY, VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("빈 INTERNAL_API_KEY → 부팅 실패 (빈 헤더 인증 우회 차단)")
    void blankInternalKey_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(VALID_JWT, "  ", VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_KEY");
    }

    @Test
    @DisplayName("dev 기본 INTERNAL_API_KEY → 부팅 실패")
    void devInternalKey_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(VALID_JWT, "change-me-before-deploy", VALID_CORS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_API_KEY");
    }

    @Test
    @DisplayName("CORS 콤마 조합에 * 포함 → 부팅 실패")
    void wildcardInCorsList_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(VALID_JWT, VALID_KEY, "https://a.com, *"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("CORS 패턴 안의 * (https://* 등) → 부팅 실패")
    void wildcardPatternInCors_fails() {
        assertThatThrownBy(() -> new ProdConfigGuard(VALID_JWT, VALID_KEY, "https://*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("문제 여러 개면 메시지에 전부 나열")
    void multipleProblems_allListed() {
        assertThatThrownBy(() -> new ProdConfigGuard("", "change-me-before-deploy", "*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("INTERNAL_API_KEY")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }
}
