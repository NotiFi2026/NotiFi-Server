package com.notifi.server.domain.auth.controller;

import com.notifi.server.domain.auth.dto.*;
import com.notifi.server.domain.auth.service.AuthService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입·로그인·JWT 토큰 관리")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "[A1] 보호자 회원가입",
               description = "이메일·비밀번호·이름·역할로 보호자 계정을 생성한다. (권한: 공개)")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements   // 공개 엔드포인트 — Swagger 자물쇠 표시 제거
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @Operation(summary = "[A2] 로그인",
               description = "이메일·비밀번호로 로그인하고 액세스·리프레시 토큰을 발급받는다. (권한: 공개)")
    @PostMapping("/login")
    @SecurityRequirements   // 공개 엔드포인트 — Swagger 자물쇠 표시 제거
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "[A3] 토큰 갱신",
               description = "리프레시 토큰으로 새 액세스 토큰을 발급받는다. 기존 리프레시 토큰은 폐기되고 새 토큰이 발급된다. (권한: 공개)")
    @PostMapping("/refresh")
    @SecurityRequirements   // 공개 엔드포인트 — Swagger 자물쇠 표시 제거
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @Operation(summary = "[A4] 로그아웃",
               description = "리프레시 토큰을 Redis에서 폐기한다. 이후 해당 리프레시 토큰으로 갱신 불가. (권한: 인증)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ApiResponse.ok();
    }
}
