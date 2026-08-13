package com.notifi.server.domain.auth.service;

import com.notifi.server.domain.auth.dto.RecipientSignupRequest;
import com.notifi.server.domain.auth.dto.RecipientSignupResponse;
import com.notifi.server.domain.auth.exception.AuthErrorCode;
import com.notifi.server.domain.auth.token.RefreshTokenStore;
import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.exception.RelationshipErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.token.InviteCodeStore;
import com.notifi.server.domain.caretarget.token.RecipientCodePayload;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CareRecipientAuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock InviteCodeStore inviteCodeStore;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock FcmTokenRepository fcmTokenRepository;

    @InjectMocks CareRecipientAuthService careRecipientAuthService;

    private static final RecipientSignupRequest REQUEST =
            new RecipientSignupRequest("RC3DE7FG", "OLD@B.COM", "pw123456", "박순자");

    @Test
    @DisplayName("signup: 이메일·비밀번호를 생략하면 서버가 만든다 — 보호자가 지어낼 것이 없다")
    void signup_generatesCredentialsWhenOmitted() {
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(45L, 1L)));
        CareTarget ct = careTarget(45L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hash");
        given(userRepository.save(any())).willAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 9L);
            return u;
        });
        given(jwtTokenProvider.createAccessToken(9L, "CARE_RECIPIENT")).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(9L, "CARE_RECIPIENT")).willReturn("refresh");

        careRecipientAuthService.signup(new RecipientSignupRequest("RC3DE7FG", null, null, null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        User created = captor.getValue();
        // 예약 도메인이라 실제 수신 가능한 주소와 충돌하지 않는다
        assertThat(created.getEmail()).isEqualTo("recipient-45@care.notifi.internal");
        // 이름을 안 주면 노인 이름을 쓴다
        assertThat(created.getName()).isEqualTo("박순자");
        // 비밀번호는 아무도 모르는 값이어야 한다 — 빈 값이면 빈 비밀번호로 로그인이 뚫린다
        ArgumentCaptor<String> pw = ArgumentCaptor.forClass(String.class);
        then(passwordEncoder).should().encode(pw.capture());
        assertThat(pw.getValue()).isNotBlank().hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("signup: 정상 가입 → CARE_RECIPIENT 계정 생성 + 노인 연결 + 토큰 발급")
    void signup_success() {
        given(userRepository.existsByEmail("old@b.com")).willReturn(false);
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(45L, 1L)));

        CareTarget ct = careTarget(45L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));

        given(passwordEncoder.encode("pw123456")).willReturn("hashed");
        User saved = User.create("old@b.com", "hashed", "박순자", Role.CARE_RECIPIENT);
        ReflectionTestUtils.setField(saved, "id", 9L);
        given(userRepository.save(any(User.class))).willReturn(saved);

        given(jwtTokenProvider.createAccessToken(9L, "CARE_RECIPIENT")).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(9L, "CARE_RECIPIENT")).willReturn("refresh");

        RecipientSignupResponse resp = careRecipientAuthService.signup(REQUEST);

        assertThat(resp.accessToken()).isEqualTo("access");
        assertThat(resp.refreshToken()).isEqualTo("refresh");
        assertThat(resp.user().role()).isEqualTo(Role.CARE_RECIPIENT);
        assertThat(resp.careTargetId()).isEqualTo(45L);
        assertThat(ct.getUserId()).isEqualTo(9L);
        then(refreshTokenStore).should().save(9L, "refresh", "CARE_RECIPIENT");
    }

    @Test
    @DisplayName("signup: 이메일 중복 → EMAIL_ALREADY_EXISTS, 코드 미소모")
    void signup_duplicateEmail_codeNotConsumed() {
        given(userRepository.existsByEmail("old@b.com")).willReturn(true);

        assertThatThrownBy(() -> careRecipientAuthService.signup(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        then(inviteCodeStore).should(never()).findAndDeleteRecipientCode(any());
    }

    @Test
    @DisplayName("signup: 무효·만료 코드 → 404 INVALID_RECIPIENT_CODE")
    void signup_invalidCode() {
        given(userRepository.existsByEmail("old@b.com")).willReturn(false);
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG")).willReturn(Optional.empty());

        assertThatThrownBy(() -> careRecipientAuthService.signup(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_RECIPIENT_CODE);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("signup: 노인이 삭제된 경우 → 404 INVALID_RECIPIENT_CODE")
    void signup_careTargetGone() {
        given(userRepository.existsByEmail("old@b.com")).willReturn(false);
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(99L, 1L)));
        given(careTargetRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> careRecipientAuthService.signup(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(RelationshipErrorCode.INVALID_RECIPIENT_CODE);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("signup: 연결된 계정이 노인이 아니면 거부 — 연결코드로 보호자 세션이 나오면 권한 상승이다")
    void signup_relinkRejectsNonRecipientAccount() {
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(45L, 1L)));
        CareTarget ct = careTarget(45L);
        ct.linkUser(8L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));

        User guardian = User.create("g@b.com", "hash", "김보호", Role.GUARDIAN);
        ReflectionTestUtils.setField(guardian, "id", 8L);
        given(userRepository.findById(8L)).willReturn(Optional.of(guardian));

        assertThatThrownBy(() -> careRecipientAuthService.signup(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED);
        then(refreshTokenStore).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("signup: 이미 연결된 노인은 기존 계정으로 재연결된다 — 로그아웃 복구 경로")
    void signup_alreadyLinked_relinks() {
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(45L, 1L)));

        CareTarget ct = careTarget(45L);
        ct.linkUser(8L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));

        User existing = User.create("old@b.com", "hash", "박순자", Role.CARE_RECIPIENT);
        ReflectionTestUtils.setField(existing, "id", 8L);
        given(userRepository.findById(8L)).willReturn(Optional.of(existing));
        given(jwtTokenProvider.createAccessToken(8L, "CARE_RECIPIENT")).willReturn("access");
        given(jwtTokenProvider.createRefreshToken(8L, "CARE_RECIPIENT")).willReturn("refresh");

        RecipientSignupResponse resp = careRecipientAuthService.signup(REQUEST);

        // 노인은 자격증명을 모르는 것이 정상이라, 재연결이 유일한 복구 경로다
        assertThat(resp.user().userId()).isEqualTo(8L);
        assertThat(resp.careTargetId()).isEqualTo(45L);
        then(userRepository).should(never()).save(any());
        then(refreshTokenStore).should().save(8L, "refresh", "CARE_RECIPIENT");
        // 기기를 바꿔 재연결하면 옛 폰 토큰이 남아 응답 못 하는 기기로 푸시가 계속 간다
        then(fcmTokenRepository).should().deleteByUserId(8L);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CareTarget careTarget(Long id) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", id);
        return ct;
    }
}
