package com.notifi.server.domain.caretarget.token;

import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class InviteCodeStore {

    private static final String KEY_PREFIX = "invite_code:";
    // 0, O, I, l, 1 제외 — 육안 혼동 방지
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${invite.code-ttl}")
    private long codeTtlSeconds;

    public String issue(InviteCodePayload payload) {
        String code = generateUniqueCode();
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(key(code), json, Duration.ofSeconds(codeTtlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("invite code serialization failed", e);
        }
        return code;
    }

    public Instant nextExpiresAt() {
        return Instant.now().plusSeconds(codeTtlSeconds);
    }

    /** 코드를 유지한 채 페이로드만 조회 — 미리보기용. */
    public Optional<InviteCodePayload> find(String code) {
        String json = redisTemplate.opsForValue().get(key(code));
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, InviteCodePayload.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Redis TTL 기반 실제 만료시각 반환 — 미리보기용. */
    public Optional<Instant> expiresAt(String code) {
        Long ttl = redisTemplate.getExpire(key(code), TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) return Optional.empty();
        return Optional.of(Instant.now().plusSeconds(ttl));
    }

    /** 코드 조회와 삭제를 원자적으로 수행 — 동시 수락 방지. */
    public Optional<InviteCodePayload> findAndDelete(String code) {
        String json = redisTemplate.opsForValue().getAndDelete(key(code));
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, InviteCodePayload.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key(code)))) {
                return code;
            }
        }
        throw new IllegalStateException("invite code generation failed after 10 attempts");
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
