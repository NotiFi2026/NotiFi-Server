package com.notifi.server.domain.auth;

import com.notifi.server.domain.auth.dto.*;
import com.notifi.server.domain.auth.token.RefreshTokenStore;
import com.notifi.server.domain.auth.exception.AuthErrorCode;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import com.notifi.server.domain.user.User;
import com.notifi.server.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
        // 토큰 파싱으로 userId 추출 (만료·위조 시 BusinessException)
        var auth = jwtTokenProvider.getAuthentication(request.refreshToken());
        Long userId = (Long) auth.getPrincipal();

        String stored = refreshTokenStore.find(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!stored.equals(request.refreshToken())) {
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

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
