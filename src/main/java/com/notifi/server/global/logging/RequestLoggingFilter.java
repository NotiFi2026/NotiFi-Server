package com.notifi.server.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 요청 진입·종료 시각을 로깅 (method, uri, status, latency).
 * 요청·응답 바디는 민감정보 및 성능 이슈로 기본 off.
 * /actuator/** 는 노이즈 방지를 위해 제외.
 *
 * <p><b>경로에 비밀이 들어오는 경우는 여기서 가린다.</b> 바디를 안 찍는다고 안전한 게 아니다 —
 * 초대코드는 URL 경로 변수라 요청당 두 줄(진입·종료)씩 평문으로 남는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /**
     * 초대·연결 코드가 들어가는 경로 세그먼트. 코드를 아는 것만으로 남의 노인에
     * 보호자로 붙을 수 있으므로(R1-b), 로그 열람 권한이 곧 초대 권한이 되면 안 된다.
     *
     * <p>{@code /accept} 같은 뒤따르는 세그먼트는 보존한다 — 어떤 요청이었는지는 남아야 한다.
     * 모든 요청이 타는 필터라 패턴은 미리 컴파일한다.
     */
    private static final Pattern SENSITIVE_PATH_SEGMENT =
            Pattern.compile("(/api/v1/invite-codes/)[^/?]+");

    private static final String MASK = "$1***";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long startMs = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = buildUri(request);

        log.info("→ {} {}", method, uri);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startMs;
            log.info("← {} {} {} ({}ms)", method, uri, response.getStatus(), durationMs);
        }
    }

    private String buildUri(HttpServletRequest request) {
        String uri = mask(request.getRequestURI());
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? uri + "?" + query : uri;
    }

    private String mask(String uri) {
        return SENSITIVE_PATH_SEGMENT.matcher(uri).replaceFirst(MASK);
    }
}
