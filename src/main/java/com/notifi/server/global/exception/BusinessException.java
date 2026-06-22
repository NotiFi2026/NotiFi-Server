package com.notifi.server.global.exception;

import lombok.Getter;

/**
 * 도메인 서비스가 던지는 비즈니스 예외.
 * ErrorCode 를 들고 다녀 GlobalExceptionHandler 가 상태 코드·메시지를 일관되게 반환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
