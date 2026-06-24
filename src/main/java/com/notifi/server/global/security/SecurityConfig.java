package com.notifi.server.global.security;

import tools.jackson.databind.ObjectMapper;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.exception.ErrorCode;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.security.internal.InternalApiKeyFilter;
import com.notifi.server.global.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.nio.charset.StandardCharsets;

/**
 * 다중 SecurityFilterChain 으로 경로별 인증 방식을 분리.
 *
 * Order(1) /internal/v1/** — X-Internal-Key (AI 서버 간 통신)
 * Order(2) /api/v1/**     — JWT Bearer (보호자 앱)
 * Order(3) 그 외          — /actuator/health 허용, 나머지 차단
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalApiKeyFilter internalApiKeyFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;

    // ── 1. 내부 API ─────────────────────────────────────────────────────────

    @Bean
    @Order(1)
    public SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // ── 2. 외부 API (보호자 앱) ──────────────────────────────────────────────

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, CommonErrorCode.INVALID_CREDENTIALS))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, CommonErrorCode.ACCESS_DENIED))
                )
                .build();
    }

    // ── 3. 기본 체인 (actuator 등) ───────────────────────────────────────────

    @Bean
    @Order(3)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().denyAll()
                )
                .build();
    }

    // ── 공용 빈 ─────────────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // @Component 필터들이 서블릿 필터로 자동 등록되는 것을 막는다.
    // SecurityFilterChain 안에서만 동작해야 하므로 자동 등록은 disabled.
    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiKeyFilter filter) {
        FilterRegistrationBean<InternalApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // ── private ─────────────────────────────────────────────────────────────

    private void writeError(jakarta.servlet.http.HttpServletResponse response, ErrorCode errorCode)
            throws java.io.IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
    }
}
