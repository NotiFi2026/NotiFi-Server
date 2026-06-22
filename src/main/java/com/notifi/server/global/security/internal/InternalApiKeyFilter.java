package com.notifi.server.global.security.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifi.server.global.exception.ErrorCode;
import com.notifi.server.global.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 내부 API(/internal/v1/**) 전용 인증 필터.
 * X-Internal-Key 헤더와 환경변수 값을 상수 시간(constant-time) 비교해 위조 방지.
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Key";

    private final byte[] expectedKey;
    private final ObjectMapper objectMapper;

    public InternalApiKeyFilter(
            @Value("${internal.api-key}") String apiKey,
            ObjectMapper objectMapper
    ) {
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestKey = request.getHeader(HEADER);
        byte[] actual = (requestKey != null ? requestKey : "").getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedKey, actual)) {
            log.warn("[InternalApi] 인증 실패 — uri={}", request.getRequestURI());
            response.setStatus(ErrorCode.INVALID_INTERNAL_KEY.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.INVALID_INTERNAL_KEY))
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
