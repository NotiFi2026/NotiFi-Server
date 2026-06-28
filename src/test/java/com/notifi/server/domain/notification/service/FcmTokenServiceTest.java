package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.notification.dto.FcmTokenRequest;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.entity.Platform;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    @Mock FcmTokenRepository fcmTokenRepository;

    @InjectMocks FcmTokenService fcmTokenService;

    // ── N3: register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: 신규 토큰 → save 호출 / userId·token·platform 일치")
    void register_newToken_inserts() {
        given(fcmTokenRepository.findByToken("token-new")).willReturn(Optional.empty());
        given(fcmTokenRepository.save(any())).willAnswer(inv -> {
            FcmToken ft = inv.getArgument(0);
            ReflectionTestUtils.setField(ft, "id", 1L);
            return ft;
        });

        fcmTokenService.register(7L, new FcmTokenRequest("token-new", Platform.ANDROID));

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        FcmToken saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getToken()).isEqualTo("token-new");
        assertThat(saved.getPlatform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("register: 이미 존재하는 토큰 → refresh(user·platform 갱신), save 미호출")
    void register_existingToken_refreshesUserAndPlatform() {
        FcmToken existing = FcmToken.create(99L, "token-exist", Platform.IOS);
        given(fcmTokenRepository.findByToken("token-exist")).willReturn(Optional.of(existing));

        fcmTokenService.register(7L, new FcmTokenRequest("token-exist", Platform.ANDROID));

        // save 미호출 — 더티체킹으로 처리
        verify(fcmTokenRepository, never()).save(any());
        // refresh 결과 반영 확인
        assertThat(existing.getUserId()).isEqualTo(7L);
        assertThat(existing.getPlatform()).isEqualTo(Platform.ANDROID);
    }

    @Test
    @DisplayName("register: 동시 등록 경합(unique 위반) → 예외 전파 없이 멱등 처리")
    void register_concurrentDuplicate_swallowed() {
        given(fcmTokenRepository.findByToken("token-race")).willReturn(Optional.empty());
        given(fcmTokenRepository.save(any())).willThrow(new DataIntegrityViolationException("dup"));

        assertThatCode(() ->
                fcmTokenService.register(7L, new FcmTokenRequest("token-race", Platform.IOS))
        ).doesNotThrowAnyException();
    }
}
