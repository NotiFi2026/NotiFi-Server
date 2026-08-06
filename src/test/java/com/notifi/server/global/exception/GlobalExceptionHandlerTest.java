package com.notifi.server.global.exception;

import com.notifi.server.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 단위 테스트.
 * Spring Boot 슬라이스 없이 standaloneSetup 으로 핸들러를 직접 연결해 신뢰성 확보.
 */
class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── 테스트용 컨트롤러 ────────────────────────────────────────────────────

    @RestController
    static class FakeController {

        @GetMapping("/test/business")
        void throwBusiness() {
            throw new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/test/unhandled")
        void throwUnhandled() {
            throw new RuntimeException("unexpected error");
        }

        @GetMapping("/test/typed/{id}")
        void typedPathVariable(@PathVariable Long id) {
        }

        @GetMapping("/test/required-param")
        void requiredParam(@RequestParam("date") String date) {
        }
    }

    // ── 테스트 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BusinessException → 명세 에러 코드·상태 코드 반환")
    void businessException_returnsErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    @DisplayName("미처리 예외 → 500 + INTERNAL_ERROR (상세 미노출)")
    void unhandledException_returns500() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    @DisplayName("경로 변수 타입 불일치 → 400 INVALID_INPUT_VALUE (500 아님)")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/test/typed/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("필수 쿼리 파라미터 누락 → 400 INVALID_INPUT_VALUE")
    void missingParameter_returns400() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }
}
