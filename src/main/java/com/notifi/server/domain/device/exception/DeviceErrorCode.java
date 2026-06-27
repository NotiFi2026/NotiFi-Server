package com.notifi.server.domain.device.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DeviceErrorCode implements ErrorCode {

    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "존재하지 않는 디바이스입니다."),
    DEVICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "DEVICE_ALREADY_EXISTS", "이미 등록된 디바이스(device_uid)입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
