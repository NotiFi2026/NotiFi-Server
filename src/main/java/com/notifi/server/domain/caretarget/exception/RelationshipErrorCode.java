package com.notifi.server.domain.caretarget.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RelationshipErrorCode implements ErrorCode {

    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND,
            "INVALID_INVITE_CODE", "유효하지 않거나 만료된 초대 코드입니다."),
    RELATIONSHIP_ALREADY_EXISTS(HttpStatus.CONFLICT,
            "RELATIONSHIP_ALREADY_EXISTS", "이미 해당 노인의 보호자로 등록되어 있습니다."),
    RELATIONSHIP_NOT_FOUND(HttpStatus.NOT_FOUND,
            "RELATIONSHIP_NOT_FOUND", "존재하지 않는 보호자 관계입니다."),
    CANNOT_DELETE_PRIMARY(HttpStatus.CONFLICT,
            "CANNOT_DELETE_PRIMARY", "주 보호자 연결은 해제할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
