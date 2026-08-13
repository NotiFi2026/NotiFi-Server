package com.notifi.server.domain.auth.service;

import com.notifi.server.domain.auth.dto.RecipientSignupRequest;
import com.notifi.server.domain.auth.dto.RecipientSignupResponse;
import com.notifi.server.domain.auth.exception.AuthErrorCode;
import com.notifi.server.domain.auth.token.RefreshTokenStore;
import com.notifi.server.domain.caretarget.entity.CareTarget;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 노인(CARE_RECIPIENT) 연결코드 가입 오케스트레이션.
 * Auth·CareTarget 교차 도메인 의존을 AuthService에 넣지 않고 여기로 격리한다.
 */
@Service
@RequiredArgsConstructor
public class CareRecipientAuthService {

    private final UserRepository userRepository;
    private final CareTargetRepository careTargetRepository;
    private final InviteCodeStore inviteCodeStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    /** 서버 생성 이메일의 도메인. 실제 수신 가능한 주소와 절대 충돌하지 않도록 예약 도메인을 쓴다. */
    private static final String GENERATED_EMAIL_DOMAIN = "@care.notifi.internal";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 연결코드로 노인 세션을 연다 — 최초 가입과 재연결을 같은 경로로 처리한다.
     *
     * <p>재연결이 필요한 이유: 노인은 이메일·비밀번호를 모르는 것이 정상이라(보호자가 만들어 준다)
     * 앱 재설치·기기 교체·장기 미사용으로 세션이 끊기면 <b>스스로 돌아올 방법이 없다.</b>
     * 비밀번호 재설정 경로도 없어서 지금까지는 보호자가 직접 찾아가야 복구됐다.
     *
     * <p>연결코드는 단발·24시간·주 보호자만 발급이라 최초 가입과 신뢰 모델이 같다.
     * 즉 재연결을 허용해도 보안 등급이 내려가지 않는다.
     */
    @Transactional
    public RecipientSignupResponse signup(RecipientSignupRequest request) {
        // 이메일을 직접 준 경우엔 코드 소모(findAndDelete) 전에 중복을 걸러낸다 — 흔한 실패로
        // 단발 코드를 태우면 보호자가 다시 발급해야 한다. 생략된 경우는 careTargetId가 필요해
        // 여기서 검사할 수 없고, 서버 생성 주소는 예약 도메인이라 애초에 충돌하지 않는다.
        if (request.email() != null && !request.email().isBlank()
                && userRepository.existsByEmail(request.email().toLowerCase(Locale.ROOT))) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        RecipientCodePayload payload = inviteCodeStore.findAndDeleteRecipientCode(request.code())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_RECIPIENT_CODE));

        CareTarget careTarget = careTargetRepository.findById(payload.careTargetId())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_RECIPIENT_CODE));

        User user = careTarget.getUserId() != null
                ? relink(careTarget)
                : createAndLink(careTarget, request);

        return issueSession(user, careTarget.getId());
    }

    /** 이미 연결된 노인 — 기존 계정 그대로 새 세션을 연다. 요청의 자격증명 필드는 무시한다. */
    private User relink(CareTarget careTarget) {
        return userRepository.findById(careTarget.getUserId())
                // 노인 계정이 사라졌는데 careTarget이 연결을 붙들고 있는 상태 — 코드로는 복구 불가
                .orElseThrow(() -> new BusinessException(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED));
    }

    private User createAndLink(CareTarget careTarget, RecipientSignupRequest request) {
        String email = resolveEmail(request.email(), careTarget.getId());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String rawPassword = request.password() != null ? request.password() : randomPassword();
        String name = request.name() != null && !request.name().isBlank()
                ? request.name()
                : careTarget.getName();

        User user;
        try {
            user = userRepository.save(User.create(
                    email, passwordEncoder.encode(rawPassword), name, Role.CARE_RECIPIENT));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        careTarget.linkUser(user.getId());
        try {
            // 커밋 시점 UNIQUE(user_id) 위반은 서비스 밖에서 500이 되므로 여기서 flush해 409로 변환
            careTargetRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED);
        }
        return user;
    }

    private RecipientSignupResponse issueSession(User user, Long careTargetId) {
        String role = user.getRole().name();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);
        // 기존 세션은 교체된다 — 한 계정 한 세션
        refreshTokenStore.save(user.getId(), refreshToken, role);
        user.recordLogin();

        return RecipientSignupResponse.of(accessToken, refreshToken, user, careTargetId);
    }

    /** 생략 시 노인별로 결정적인 내부 주소를 만든다 — 보호자가 가짜 이메일을 지어낼 필요가 없다. */
    private String resolveEmail(String requested, Long careTargetId) {
        if (requested != null && !requested.isBlank()) {
            return requested.toLowerCase(Locale.ROOT);
        }
        return "recipient-" + careTargetId + GENERATED_EMAIL_DOMAIN;
    }

    /**
     * 아무도 모르는 비밀번호. 노인 계정의 로그인 경로는 연결코드 하나뿐이므로 이 값은 쓰이지 않는다 —
     * {@code password_hash}가 NOT NULL이고, 빈 값을 넣으면 빈 비밀번호로 로그인이 뚫린다.
     */
    private String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
