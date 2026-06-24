package com.notifi.server.domain.auth.service;

import com.notifi.server.domain.auth.dto.*;
import com.notifi.server.domain.auth.token.RefreshTokenStore;
import com.notifi.server.domain.auth.exception.AuthErrorCode;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), request.name(), request.role());
        try {
            return SignupResponse.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 경합으로 unique 제약 위반 시 409로 변환
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_CREDENTIALS));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(CommonErrorCode.INVALID_CREDENTIALS);
        }

        String role = user.getRole().name();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);

        refreshTokenStore.save(user.getId(), refreshToken);
        user.recordLogin();

        return LoginResponse.of(accessToken, refreshToken, user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        // JwtTokenProvider는 액세스/리프레시 토큰을 구분하지 않고 INVALID_CREDENTIALS를 던지므로
        // 리프레시 컨텍스트(만료·위조·형식 오류)에서는 INVALID_REFRESH_TOKEN으로 재매핑
        var auth = parseRefreshToken(request.refreshToken());
        Long userId = (Long) auth.getPrincipal();

        String stored = refreshTokenStore.find(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!stored.equals(request.refreshToken())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 유효 토큰 확인 후에만 DB 조회 — 비활성 계정 갱신 차단 (login 차단과 대칭)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        if (!user.isActive()) {
            refreshTokenStore.delete(userId);   // 세션 완전 종료
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        String newAccess = jwtTokenProvider.createAccessToken(userId, role);
        String newRefresh = jwtTokenProvider.createRefreshToken(userId, role);

        refreshTokenStore.save(userId, newRefresh);

        return new TokenResponse(newAccess, newRefresh);
    }

    public void logout(Long userId) {
        refreshTokenStore.delete(userId);
    }

    private Authentication parseRefreshToken(String token) {
        try {
            return jwtTokenProvider.getAuthentication(token);
        } catch (BusinessException e) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
