package com.notifi.server.domain.auth.token;

import com.notifi.server.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Redis 키 수명을 토큰 자체의 수명과 맞춘다.
     *
     * <p>둘이 어긋나면 어느 쪽이 짧든 그쪽이 실질 만료가 된다 — 노인 토큰을 90일로 발급해도
     * Redis가 7일에 지우면 7일 만에 로그아웃된다. 만료 정책의 출처를 하나로 둔다.
     */
    public void save(Long userId, String token, String role) {
        redisTemplate.opsForValue().set(
                key(userId), token, Duration.ofSeconds(jwtTokenProvider.refreshTokenTtlFor(role)));
    }

    public Optional<String> find(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
