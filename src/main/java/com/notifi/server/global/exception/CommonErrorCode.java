package com.notifi.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 횡단 공통 에러 코드.
 * global 패키지(Security, Filter, Handler)에서 직접 사용.
 * 도메인 전용 코드는 각 도메인의 XxxErrorCode 에 위치.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // ── 인증 ─────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
            "TOKEN_EXPIRED", "액세스 토큰이 만료되었습니다."),

    // ── 권한 ─────────────────────────────────────────
    ACCESS_DENIED(HttpStatus.FORBIDDEN,
            "ACCESS_DENIED", "해당 노인에 대한 접근 권한이 없습니다."),

    // ── 리소스 없음 ───────────────────────────────────
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),

    // ── 내부 API ──────────────────────────────────────
    INVALID_INTERNAL_KEY(HttpStatus.UNAUTHORIZED,
            "INVALID_INTERNAL_KEY", "내부 API 인증 키가 올바르지 않습니다."),

    // ── 요청 검증 ─────────────────────────────────────
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST,
            "INVALID_INPUT_VALUE", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),

    // ── 서버 ─────────────────────────────────────────
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
