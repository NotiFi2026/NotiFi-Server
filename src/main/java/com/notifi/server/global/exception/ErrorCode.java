package com.notifi.server.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 에러 코드 계약.
 * 구현체: CommonErrorCode (횡단), 각 도메인의 XxxErrorCode (예: AuthErrorCode).
 */
public interface ErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
