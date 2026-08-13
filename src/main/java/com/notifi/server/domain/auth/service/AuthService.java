package com.notifi.server.domain.auth.service;

import com.notifi.server.domain.auth.dto.*;
import com.notifi.server.domain.auth.token.RefreshTokenStore;
import com.notifi.server.domain.auth.exception.AuthErrorCode;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
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
    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 자가 가입은 보호자·사회복지사만 허용 — CARE_RECIPIENT는 연결코드 가입(A5) 전용, ADMIN은 자가 등록 불가
        if (request.role() != Role.GUARDIAN && request.role() != Role.SOCIAL_WORKER) {
            throw new BusinessException(AuthErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        }
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

        refreshTokenStore.save(user.getId(), refreshToken, role);
        user.recordLogin();

        return LoginResponse.of(accessToken, refreshToken, user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        // 리프레시 컨텍스트(만료·위조·형식 오류·용도 불일치)에서는 INVALID_REFRESH_TOKEN으로 재매핑
        Long userId = parseRefreshToken(request.refreshToken());

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

        // role은 토큰 클레임이 아닌 DB 기준 — 역할 변경이 다음 갱신부터 즉시 반영
        String role = user.getRole().name();
        String newAccess = jwtTokenProvider.createAccessToken(userId, role);
        String newRefresh = jwtTokenProvider.createRefreshToken(userId, role);

        refreshTokenStore.save(userId, newRefresh, role);

        return new TokenResponse(newAccess, newRefresh);
    }

    /**
     * 세션 종료 — 리프레시 토큰과 함께 <b>FCM 토큰도 지운다.</b>
     *
     * <p>토큰을 남겨 두면 로그아웃된 폰에 푸시가 계속 나가고 서버는 그걸 발송 성공으로 기록한다.
     * 노인 폰에서 이게 특히 나쁘다 — 음성 확인 알림은 뜨는데 눌러도 로그인 화면이라 응답할 수
     * 없고, 서버 관점에선 모든 게 정상이라 <b>아무도 실패를 모른다.</b>
     * 토큰을 지우면 발송 대상이 0이 되어 최소한 관측 가능한 실패가 된다.
     */
    @Transactional
    public void logout(Long userId) {
        // DB를 먼저 지운다 — 실패하면 트랜잭션이 롤백되어 아무 일도 안 일어난 상태로 끝난다.
        // 반대 순서면 Redis만 지워져 "로그아웃됐는데 FCM 토큰은 남은" 상태가 되는데,
        // 그게 정확히 이 메서드가 없애려는 상태다.
        fcmTokenRepository.deleteByUserId(userId);
        refreshTokenStore.delete(userId);
    }

    private Long parseRefreshToken(String token) {
        try {
            return jwtTokenProvider.getRefreshTokenUserId(token);
        } catch (BusinessException e) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
