package com.notifi.server.domain.auth.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Auth 도메인 전용 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED,
            "INVALID_REFRESH_TOKEN", "리프레시 토큰이 유효하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
