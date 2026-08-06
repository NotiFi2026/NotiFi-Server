package com.notifi.server.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * prod 프로파일 부팅 검증 — 환경 변수 누락으로 dev 기본 시크릿이 조용히 살아나는 것을 차단한다.
 * 기본값 목록은 application.yaml 의 placeholder 기본값과 일치해야 한다.
 */
@Component
@Profile("prod")
public class ProdConfigGuard {

    private static final String DEV_JWT_SECRET = "notifi-secret-key-for-local-dev-only-change-in-prod!";
    private static final String DEV_INTERNAL_API_KEY = "change-me-before-deploy";

    public ProdConfigGuard(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${internal.api-key}") String internalApiKey,
            @Value("${cors.allowed-origins}") String corsAllowedOrigins
    ) {
        List<String> problems = new ArrayList<>();
        if (DEV_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET 이 dev 기본값입니다");
        }
        if (DEV_INTERNAL_API_KEY.equals(internalApiKey)) {
            problems.add("INTERNAL_API_KEY 가 dev 기본값입니다");
        }
        if ("*".equals(corsAllowedOrigins.trim())) {
            problems.add("CORS_ALLOWED_ORIGINS 가 * 입니다 — 실제 도메인으로 제한하세요");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("prod 설정 검증 실패: " + String.join(", ", problems));
        }
    }
}
