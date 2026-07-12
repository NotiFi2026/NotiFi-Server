package com.notifi.server.domain.caretarget.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * CareTarget 도메인 전용 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum CareTargetErrorCode implements ErrorCode {

    CARE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND,
            "CARE_TARGET_NOT_FOUND", "존재하지 않는 노인입니다."),
    CARE_TARGET_ALREADY_LINKED(HttpStatus.CONFLICT,
            "CARE_TARGET_ALREADY_LINKED", "이미 본인 계정이 연결된 노인입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
