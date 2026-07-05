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
import com.notifi.server.domain.user.entity.Role;
import com.notifi.server.domain.user.entity.User;
import com.notifi.server.domain.user.repository.UserRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks CareRecipientAuthService careRecipientAuthService;

    private static final RecipientSignupRequest REQUEST =
            new RecipientSignupRequest("RC3DE7FG", "OLD@B.COM", "pw123456", "박순자");

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
        then(refreshTokenStore).should().save(9L, "refresh");
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
    @DisplayName("signup: 이미 계정 연결된 노인 → 409 CARE_TARGET_ALREADY_LINKED")
    void signup_alreadyLinked() {
        given(userRepository.existsByEmail("old@b.com")).willReturn(false);
        given(inviteCodeStore.findAndDeleteRecipientCode("RC3DE7FG"))
                .willReturn(Optional.of(new RecipientCodePayload(45L, 1L)));

        CareTarget ct = careTarget(45L);
        ct.linkUser(8L);
        given(careTargetRepository.findById(45L)).willReturn(Optional.of(ct));

        assertThatThrownBy(() -> careRecipientAuthService.signup(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED);
        then(userRepository).should(never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private CareTarget careTarget(Long id) {
        CareTarget ct = CareTarget.create("박순자", null, Gender.FEMALE, null, null);
        ReflectionTestUtils.setField(ct, "id", id);
        return ct;
    }
}
