package com.notifi.server.global.response;

import com.notifi.server.global.exception.ErrorCode;

/**
 * 모든 API 응답의 공통 envelope.
 * 성공: { success:true, data:{...}, error:null }
 * 실패: { success:false, data:null, error:{code, message} }
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error
) {

    public record ErrorBody(String code, String message) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 응답 바디 없는 성공 (204 No Content 용). */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<?> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null,
                new ErrorBody(errorCode.getCode(), errorCode.getMessage()));
    }

    /** 유효성 검증 실패 등 커스텀 메시지가 필요한 경우. */
    public static ApiResponse<?> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null,
                new ErrorBody(errorCode.getCode(), message));
    }
}
