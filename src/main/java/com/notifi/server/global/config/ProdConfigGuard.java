package com.notifi.server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * prod 프로파일 부팅 검증 — 환경 변수 누락으로 dev 기본 시크릿이 조용히 살아나는 것을 차단한다.
 * 블록리스트는 application.yaml 의 placeholder 기본값 + .env.example 의 예시 값과 일치해야 한다.
 */
@Component
@Profile("prod")
public class ProdConfigGuard {

    private static final Set<String> BLOCKED_JWT_SECRETS = Set.of(
            "notifi-secret-key-for-local-dev-only-change-in-prod!",       // application.yaml 기본값
            "your-secret-key-must-be-at-least-32-characters-long!"        // .env.example 예시 값
    );
    private static final String DEV_INTERNAL_API_KEY = "change-me-before-deploy";

    public ProdConfigGuard(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${cors.allowed-origins}") String corsAllowedOrigins
    ) {
        List<String> problems = new ArrayList<>();
        if (BLOCKED_JWT_SECRETS.contains(jwtSecret)) {
            problems.add("JWT_SECRET 이 dev 기본값/예시값입니다");
        }
        if (DEV_INTERNAL_API_KEY.equals(internalApiKey)) {
            problems.add("INTERNAL_API_KEY 가 dev 기본값입니다");
        }
        // CorsConfig 와 동일하게 콤마 분리 — "https://a.com,*" 같은 조합도 차단
        boolean hasWildcardOrigin = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .anyMatch("*"::equals);
        if (hasWildcardOrigin) {
            problems.add("CORS_ALLOWED_ORIGINS 에 * 가 포함돼 있습니다 — 실제 도메인으로 제한하세요");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("prod 설정 검증 실패: " + String.join(", ", problems));
        }
    }
}
