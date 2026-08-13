package com.notifi.server.domain.report.exception;

import com.notifi.server.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "일일 리포트를 찾을 수 없습니다."),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "REPORT_ALREADY_EXISTS", "같은 날짜의 리포트가 방금 적재되었습니다. 재시도하면 갱신됩니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
