package com.notifi.server.auth;

import com.notifi.server.auth.dto.*;
import com.notifi.server.auth.token.RefreshTokenStore;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.ErrorCode;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import com.notifi.server.user.Role;
import com.notifi.server.user.User;
import com.notifi.server.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: 정상 로그인 후 토큰·사용자 정보 반환")
    void login_success() {
        User user = User.create("a@b.com", "hashed", "김보호", Role.GUARDIAN);
        given(userRepository.findByEmail("a@b.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw123456", "hashed")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(any(), eq("GUARDIAN"))).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(any(), eq("GUARDIAN"))).willReturn("refresh");

        LoginResponse resp = authService.login(new LoginRequest("a@b.com", "pw123456"));

        assertThat(resp.accessToken()).isEqualTo("access");
        assertThat(resp.refreshToken()).isEqualTo("refresh");
        assertThat(resp.user().name()).isEqualTo("김보호");
        then(refreshTokenStore).should().save(any(), eq("refresh"));
    }

    @Test
    @DisplayName("login: 없는 이메일 → INVALID_CREDENTIALS")
    void login_userNotFound() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@b.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
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
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
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
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        // bcrypt 연산 호출 없어야 함 (비활성 계정은 조기 차단)
        then(passwordEncoder).shouldHaveNoInteractions();
    }

    // ── refresh ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: 정상 갱신 + 토큰 회전")
    void refresh_success() {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_GUARDIAN")));
        given(jwtTokenProvider.getAuthentication("old-refresh")).willReturn(auth);
        given(refreshTokenStore.find(1L)).willReturn(Optional.of("old-refresh"));
        given(jwtTokenProvider.createAccessToken(1L, "GUARDIAN")).willReturn("new-access");
        given(jwtTokenProvider.createRefreshToken(1L, "GUARDIAN")).willReturn("new-refresh");

        TokenResponse resp = authService.refresh(new RefreshRequest("old-refresh"));

        assertThat(resp.accessToken()).isEqualTo("new-access");
        assertThat(resp.refreshToken()).isEqualTo("new-refresh");
        then(refreshTokenStore).should().save(1L, "new-refresh");
    }

    @Test
    @DisplayName("refresh: Redis에 토큰 없으면 INVALID_REFRESH_TOKEN")
    void refresh_notInRedis() {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_GUARDIAN")));
        given(jwtTokenProvider.getAuthentication("token")).willReturn(auth);
        given(refreshTokenStore.find(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("refresh: Redis 값과 불일치 → INVALID_REFRESH_TOKEN")
    void refresh_tokenMismatch() {
        var auth = new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_GUARDIAN")));
        given(jwtTokenProvider.getAuthentication("incoming")).willReturn(auth);
        given(refreshTokenStore.find(1L)).willReturn(Optional.of("stored-different"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("incoming")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ── logout ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: RefreshTokenStore.delete 호출")
    void logout_deletesToken() {
        authService.logout(1L);
        then(refreshTokenStore).should().delete(1L);
    }
}
