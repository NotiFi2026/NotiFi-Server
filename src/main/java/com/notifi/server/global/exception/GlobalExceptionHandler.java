package com.notifi.server.global.exception;

import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.exception.CommonErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 컨트롤러 레이어에서 발생한 예외를 ApiResponse envelope 로 통일.
 * 필터 레이어 인증 예외(401/403)는 SecurityConfig 의 EntryPoint/AccessDeniedHandler 에서 처리.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 도메인 비즈니스 예외 — 4xx warn, 5xx error 수준 로깅 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[BusinessException] {}", e.getMessage(), e);
        } else {
            log.warn("[BusinessException] {}", e.getMessage());
        }
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code));
    }

    /** Bean Validation (@Valid) 실패 — 필드별 메시지 조합 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[Validation] {}", detail);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, detail));
    }

    /** @Validated 메서드 파라미터 검증 실패 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("[ConstraintViolation] {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    /** JSON 파싱 실패 (요청 바디 형식 오류) */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[NotReadable] {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE));
    }

    /** 경로 변수·쿼리 파라미터 타입 불일치 (예: /escalations/abc, event_type=BOGUS) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[TypeMismatch] {}={}", e.getName(), e.getValue());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, e.getName() + ": 형식이 올바르지 않습니다"));
    }

    /** 필수 쿼리 파라미터 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("[MissingParameter] {}", e.getParameterName());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, e.getParameterName() + ": 필수 파라미터입니다"));
    }

    /** 지원하지 않는 Content-Type (415) */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("[MediaTypeNotSupported] {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(CommonErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 Content-Type 입니다"));
    }

    /** 지원하지 않는 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    /** 존재하지 않는 경로 (404) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 최후 방어선 — 처리되지 않은 예외. 스택 포함 error 로깅, 클라이언트엔 상세 미노출 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("[UnhandledException] {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(CommonErrorCode.INTERNAL_ERROR));
    }
}
