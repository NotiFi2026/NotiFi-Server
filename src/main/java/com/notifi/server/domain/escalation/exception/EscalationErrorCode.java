package com.notifi.server.domain.escalation.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EscalationErrorCode implements ErrorCode {

    ESCALATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ESCALATION_NOT_FOUND", "에스컬레이션을 찾을 수 없습니다."),
    ESCALATION_ALREADY_RESOLVED(HttpStatus.CONFLICT, "ESCALATION_ALREADY_RESOLVED", "이미 종료된 에스컬레이션입니다."),
    INVALID_RESOLUTION_TYPE(HttpStatus.BAD_REQUEST, "INVALID_RESOLUTION_TYPE", "보호자 해제 시 resolution_type은 GUARDIAN_HANDLED 또는 FALSE_ALARM 이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
