package com.notifi.server.domain.notification.repository;

import com.notifi.server.domain.notification.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByToken(String token);
    List<FcmToken> findByUserIdIn(Collection<Long> userIds);

    // 로그아웃 시 정리 — 남겨 두면 응답할 수 없는 폰에 푸시가 계속 나가고 발송은 성공으로 기록된다
    void deleteByUserId(Long userId);
}
