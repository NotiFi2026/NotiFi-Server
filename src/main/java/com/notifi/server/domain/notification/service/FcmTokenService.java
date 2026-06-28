package com.notifi.server.domain.notification.service;

import com.notifi.server.domain.notification.dto.FcmTokenRequest;
import com.notifi.server.domain.notification.entity.FcmToken;
import com.notifi.server.domain.notification.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;

    /**
     * FCM 토큰을 등록하거나 갱신한다.
     * - 이미 존재하는 토큰: user_id·platform 갱신 (더티체킹, 다른 보호자 로그인 포함)
     * - 신규 토큰: insert
     * - 동시 등록 경합(unique 위반): 멱등 처리 (이미 적재됨)
     */
    public void register(Long userId, FcmTokenRequest request) {
        FcmToken existing = fcmTokenRepository.findByToken(request.fcmToken()).orElse(null);
        if (existing != null) {
            existing.refresh(userId, request.platform());
            return;
        }
        try {
            fcmTokenRepository.save(FcmToken.create(userId, request.fcmToken(), request.platform()));
        } catch (DataIntegrityViolationException e) {
            // 동시 등록 경합으로 token unique 위반 — 이미 적재됨, 멱등 처리
        }
    }
}
