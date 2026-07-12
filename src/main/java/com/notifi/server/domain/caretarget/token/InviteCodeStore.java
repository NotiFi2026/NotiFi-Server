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
    // 노인 계정 연결코드 — 보호자 초대코드와 키 분리로 교차 사용 원천 차단
    private static final String RECIPIENT_KEY_PREFIX = "recipient_code:";
    // 0, O, I, l, 1 제외 — 육안 혼동 방지
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${invite.code-ttl}")
    private long codeTtlSeconds;

    public String issue(InviteCodePayload payload) {
        return issueWithPrefix(KEY_PREFIX, payload);
    }

    public String issueRecipientCode(RecipientCodePayload payload) {
        return issueWithPrefix(RECIPIENT_KEY_PREFIX, payload);
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
        return getAndDelete(KEY_PREFIX, code, InviteCodePayload.class);
    }

    /** 노인 연결코드 조회·삭제 원자 수행 — 동시 가입 방지. */
    public Optional<RecipientCodePayload> findAndDeleteRecipientCode(String code) {
        return getAndDelete(RECIPIENT_KEY_PREFIX, code, RecipientCodePayload.class);
    }

    private String issueWithPrefix(String prefix, Object payload) {
        String code = generateUniqueCode(prefix);
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(prefix + code, json, Duration.ofSeconds(codeTtlSeconds));
        } catch (Exception e) {
            throw new IllegalStateException("invite code serialization failed", e);
        }
        return code;
    }

    private <T> Optional<T> getAndDelete(String prefix, String code, Class<T> type) {
        String json = redisTemplate.opsForValue().getAndDelete(prefix + code);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String generateUniqueCode(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(prefix + code))) {
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
