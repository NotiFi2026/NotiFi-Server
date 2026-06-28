package com.notifi.server.domain.sensing.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SensingErrorCode implements ErrorCode {

    SENSING_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SENSING_EVENT_NOT_FOUND", "센싱 이벤트를 찾을 수 없습니다."),
    POSE_CLIP_NOT_FOUND(HttpStatus.NOT_FOUND, "POSE_CLIP_NOT_FOUND", "포즈 클립을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
