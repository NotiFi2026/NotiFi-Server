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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock FcmTokenRepository fcmTokenRepository;

    @InjectMocks AuthService authService;

    // ── signup ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("signup: 정상 가입 후 SignupResponse 반환")
    void signup_success() {
        given(userRepository.existsByEmail("a@b.com")).willReturn(false);
        given(passwordEncoder.encode("pw123456")).willReturn("hashed");
        User saved = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        given(userRepository.save(any(User.class))).willReturn(saved);

        SignupResponse resp = authService.signup(new SignupRequest("A@B.COM", "pw123456", "김보호", Role.GUARDIAN));

        assertThat(resp.name()).isEqualTo("김보호");
        assertThat(resp.role()).isEqualTo(Role.GUARDIAN);
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("signup: 이메일 중복 시 EMAIL_ALREADY_EXISTS")
    void signup_duplicateEmail() {
        given(userRepository.existsByEmail("a@b.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("a@b.com", "pw123456", "김보호", Role.GUARDIAN)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("signup: ADMIN 자가가입 → SIGNUP_ROLE_NOT_ALLOWED")
    void signup_adminBlocked() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("admin@b.com", "pw123456", "관리자", Role.ADMIN)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("signup: CARE_RECIPIENT 자가가입 → SIGNUP_ROLE_NOT_ALLOWED")
    void signup_careRecipientBlocked() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("old@b.com", "pw123456", "김노인", Role.CARE_RECIPIENT)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.SIGNUP_ROLE_NOT_ALLOWED);
        // role 검사가 최우선 — 이메일 조회조차 없어야 함
        then(userRepository).shouldHaveNoInteractions();
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: 정상 로그인 후 토큰·사용자 정보 반환")
    void login_success() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        ReflectionTestUtils.setField(user, "id", 1L);   // DB 생성 id 시뮬레이션
        given(userRepository.findByEmail("a@b.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw123456", "hashed")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(eq(1L), eq("GUARDIAN"))).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(eq(1L), eq("GUARDIAN"))).willReturn("refresh");

        LoginResponse resp = authService.login(new LoginRequest("a@b.com", "pw123456"));

        assertThat(resp.accessToken()).isEqualTo("access");
        assertThat(resp.refreshToken()).isEqualTo("refresh");
        assertThat(resp.user().name()).isEqualTo("김보호");
        then(refreshTokenStore).should().save(eq(1L), eq("refresh"), eq("GUARDIAN"));
    }

    @Test
    @DisplayName("login: 없는 이메일 → INVALID_CREDENTIALS")
    void login_userNotFound() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@b.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("login: 비밀번호 불일치 → INVALID_CREDENTIALS")
    void login_wrongPassword() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        given(userRepository.findByEmail("a@b.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("login: 비활성 계정 → INVALID_CREDENTIALS (계정 상태 열거 방지)")
    void login_inactiveUser() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        user.deactivate();
        given(userRepository.findByEmail("a@b.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("a@b.com", "pw123456")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_CREDENTIALS);
        // bcrypt 연산 호출 없어야 함 (비활성 계정은 조기 차단)
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    // ── refresh ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: 정상 갱신 + 토큰 회전 (role은 DB 기준)")
    void refresh_success() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        given(jwtTokenProvider.getRefreshTokenUserId("old-refresh")).willReturn(1L);
        given(refreshTokenStore.find(1L)).willReturn(Optional.of("old-refresh"));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtTokenProvider.createAccessToken(1L, "GUARDIAN")).willReturn("new-access");
        given(jwtTokenProvider.createRefreshToken(1L, "GUARDIAN")).willReturn("new-refresh");

        TokenResponse resp = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(resp.accessToken()).isEqualTo("new-access");
        assertThat(resp.refreshToken()).isEqualTo("new-refresh");
        then(refreshTokenStore).should().save(1L, "new-refresh", "GUARDIAN");
    }

    @Test
    @DisplayName("refresh: 비활성 계정 → 토큰 삭제 후 INVALID_REFRESH_TOKEN")
    void refresh_inactiveUser() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        user.deactivate();
        given(jwtTokenProvider.getRefreshTokenUserId("old-refresh")).willReturn(1L);
        given(refreshTokenStore.find(1L)).willReturn(Optional.of("old-refresh"));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-refresh")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokenStore).should().delete(1L);   // 세션 강제 종료 확인
    }

    @Test
    @DisplayName("refresh: Redis에 토큰 없으면 INVALID_REFRESH_TOKEN")
    void refresh_notInRedis() {
        given(jwtTokenProvider.getRefreshTokenUserId("token")).willReturn(1L);
        given(refreshTokenStore.find(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("refresh: Redis 값과 불일치 → INVALID_REFRESH_TOKEN")
    void refresh_tokenMismatch() {
        given(jwtTokenProvider.getRefreshTokenUserId("incoming")).willReturn(1L);
        given(refreshTokenStore.find(1L)).willReturn(Optional.of("stored-different"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("incoming")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("refresh: 토큰 파싱·용도 검증 실패 → INVALID_REFRESH_TOKEN 재매핑")
    void refresh_invalidToken() {
        given(jwtTokenProvider.getRefreshTokenUserId("access-token"))
                .willThrow(new BusinessException(CommonErrorCode.INVALID_CREDENTIALS));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("access-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokenStore).shouldHaveNoInteractions();
    }

    // ── logout ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: 리프레시 토큰과 FCM 토큰을 함께 지운다")
    void logout_deletesTokens() {
        authService.logout(1L);

        then(refreshTokenStore).should().delete(1L);
        // FCM 토큰을 남기면 로그아웃된 폰에 푸시가 계속 나가고 서버는 성공으로 기록한다 —
        // 노인 폰에선 음성 확인 알림이 떠도 응답할 수 없어 아무도 실패를 모른다
        then(fcmTokenRepository).should().deleteByUserId(1L);
    }
}
