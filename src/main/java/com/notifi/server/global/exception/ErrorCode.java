package com.notifi.server.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 에러 코드.
 * api-spec.md 1-6 에러 코드와 1:1 대응.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 인증 ─────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED,
            "TOKEN_EXPIRED", "액세스 토큰이 만료되었습니다."),

    // ── 권한 ─────────────────────────────────────────
    ACCESS_DENIED(HttpStatus.FORBIDDEN,
            "ACCESS_DENIED", "해당 노인에 대한 접근 권한이 없습니다."),

    // ── 리소스 없음 ───────────────────────────────────
    CARE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND,
            "CARE_TARGET_NOT_FOUND", "해당 노인을 찾을 수 없습니다."),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "DEVICE_NOT_FOUND", "디바이스를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),

    // ── 중복 ─────────────────────────────────────────
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    RELATIONSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "RELATIONSHIP_ALREADY_EXISTS", "이미 연결된 보호자-노인 관계입니다."),

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
