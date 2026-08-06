package com.notifi.server.domain.sensing.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SensingErrorCode implements ErrorCode {

    SENSING_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SENSING_EVENT_NOT_FOUND", "센싱 이벤트를 찾을 수 없습니다."),
    POSE_CLIP_NOT_FOUND(HttpStatus.NOT_FOUND, "POSE_CLIP_NOT_FOUND", "포즈 클립을 찾을 수 없습니다."),
    DUPLICATE_SENSING_EVENT(HttpStatus.CONFLICT, "DUPLICATE_SENSING_EVENT", "이미 적재된 센싱 이벤트입니다. 재시도하면 기존 결과를 반환합니다."),
    DUPLICATE_POSE_CLIP(HttpStatus.CONFLICT, "DUPLICATE_POSE_CLIP", "이미 적재된 포즈 클립입니다. 재시도하면 기존 결과를 반환합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
