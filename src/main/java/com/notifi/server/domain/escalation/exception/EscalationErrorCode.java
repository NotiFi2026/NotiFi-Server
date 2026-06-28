package com.notifi.server.domain.escalation.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EscalationErrorCode implements ErrorCode {

    ESCALATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ESCALATION_NOT_FOUND", "에스컬레이션을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
