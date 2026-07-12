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

    @Transactional
    public RecipientSignupResponse signup(RecipientSignupRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);

        // 코드 소모(findAndDelete) 전에 가장 흔한 실패를 먼저 걸러낸다
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        RecipientCodePayload payload = inviteCodeStore.findAndDeleteRecipientCode(request.code())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_RECIPIENT_CODE));

        CareTarget careTarget = careTargetRepository.findById(payload.careTargetId())
                .orElseThrow(() -> new BusinessException(RelationshipErrorCode.INVALID_RECIPIENT_CODE));
        if (careTarget.getUserId() != null) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_ALREADY_LINKED);
        }

        User user;
        try {
            user = userRepository.save(User.create(
                    email, passwordEncoder.encode(request.password()), request.name(), Role.CARE_RECIPIENT));
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

        String role = user.getRole().name();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), role);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), role);
        refreshTokenStore.save(user.getId(), refreshToken);
        user.recordLogin();

        return RecipientSignupResponse.of(accessToken, refreshToken, user, careTarget.getId());
    }
}
