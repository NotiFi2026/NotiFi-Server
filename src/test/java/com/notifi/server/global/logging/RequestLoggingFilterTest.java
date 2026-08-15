package com.notifi.server.global.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 요청 로그가 초대코드를 흘리지 않는지.
 *
 * <p>초대코드는 아는 것만으로 남의 노인에 보호자로 붙을 수 있는 자격증명인데, R1-c·R1-b가
 * 코드를 <b>경로 변수</b>로 받는다. 바디를 안 찍는다는 이유로 안전하다고 볼 수 없는 이유다.
 */
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    private String loggedUri(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return (String) ReflectionTestUtils.invokeMethod(filter, "buildUri", request);
    }

    @Test
    @DisplayName("초대코드 미리보기(R1-c) 경로의 코드는 마스킹된다")
    void previewPath_codeIsMasked() {
        assertThat(loggedUri("/api/v1/invite-codes/ABCD2345"))
                .isEqualTo("/api/v1/invite-codes/***")
                .doesNotContain("ABCD2345");
    }

    @Test
    @DisplayName("초대코드 수락(R1-b) 경로도 마스킹하되 뒤 세그먼트는 남긴다")
    void acceptPath_codeIsMaskedButActionRemains() {
        assertThat(loggedUri("/api/v1/invite-codes/ABCD2345/accept"))
                .isEqualTo("/api/v1/invite-codes/***/accept");
    }

    @Test
    @DisplayName("다른 경로는 그대로 남는다 — 마스킹이 넓어지면 로그를 못 쓴다")
    void otherPaths_areUntouched() {
        assertThat(loggedUri("/api/v1/care-targets/45/devices"))
                .isEqualTo("/api/v1/care-targets/45/devices");
        assertThat(loggedUri("/api/v1/care-targets/45/invite-codes"))
                .isEqualTo("/api/v1/care-targets/45/invite-codes");
    }

    @Test
    @DisplayName("쿼리스트링이 붙어도 코드는 남지 않는다")
    void queryString_isPreservedWithoutLeakingCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/invite-codes/ABCD2345");
        request.setQueryString("from=link");

        String uri = (String) ReflectionTestUtils.invokeMethod(filter, "buildUri", request);

        assertThat(uri).isEqualTo("/api/v1/invite-codes/***?from=link");
    }

    @Test
    @DisplayName("actuator는 필터를 타지 않는다")
    void actuator_isSkipped() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        boolean skipped = Boolean.TRUE.equals(
                ReflectionTestUtils.invokeMethod(filter, "shouldNotFilter", request));

        assertThat(skipped).isTrue();
    }

    @Test
    @DisplayName("체인은 그대로 통과한다 — 로깅이 요청을 막으면 안 된다")
    void chainProceeds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/invite-codes/ABCD2345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        org.mockito.Mockito.verify(chain).doFilter(request, response);
    }
}
