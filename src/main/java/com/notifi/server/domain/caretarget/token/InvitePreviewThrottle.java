package com.notifi.server.domain.caretarget.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 초대코드 미리보기(R1-c) 프로빙 제한.
 *
 * <p>미리보기는 코드를 소모하지 않는다. 그래서 8자리 코드를 무한히 시도해 유효한 것을 찾아낼 수
 * 있고, 맞히면 <b>노인 이름과 초대자 이름이 노출</b>된다. 코드 공간이 넓어도 시도 비용이 0이면
 * 시간 문제일 뿐이다.
 *
 * <p><b>실패만 센다.</b> 정상적으로 초대 링크를 받은 사람은 한 번에 맞히므로 절대 걸리지 않는다.
 * 키는 인증 사용자 기준이다 — R1-c는 이미 인증이 필요하고, IP는 NAT·프록시 뒤에서 서로 다른
 * 사용자를 한 덩어리로 묶어 애먼 사람을 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitePreviewThrottle {

    private static final String KEY_PREFIX = "invite_preview_fail:";
    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    /**
     * 이 사용자가 제한에 걸렸는지. <b>Redis 장애 시 통과시킨다(fail-open)</b> —
     * 초대 수락은 서비스 진입 경로라 부가 방어 때문에 막히면 손해가 더 크다.
     */
    public boolean isBlocked(Long userId) {
        try {
            String failures = redisTemplate.opsForValue().get(key(userId));
            return failures != null && Integer.parseInt(failures) >= MAX_FAILURES;
        } catch (Exception e) {
            log.warn("[Invite] 프로빙 제한 조회 실패 — 통과시킴 (userId={})", userId, e);
            return false;
        }
    }

    /** 잘못된 코드 조회 1회 기록. 창(10분)은 첫 실패 시점부터 시작한다. */
    public void recordFailure(Long userId) {
        try {
            Long count = redisTemplate.opsForValue().increment(key(userId));
            if (count != null && count == 1L) {
                redisTemplate.expire(key(userId), WINDOW);
            }
        } catch (Exception e) {
            log.warn("[Invite] 프로빙 실패 기록 실패 (userId={})", userId, e);
        }
    }

    /** 유효한 코드를 찾았으면 초기화 — 오타 몇 번이 다음 초대까지 따라가면 안 된다. */
    public void reset(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception e) {
            log.warn("[Invite] 프로빙 카운터 초기화 실패 (userId={})", userId, e);
        }
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
