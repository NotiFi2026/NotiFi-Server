package com.notifi.server.domain.caretarget.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * 프로빙 제한은 <b>부가 방어</b>다. 초대 수락은 서비스 진입 경로라, 이 방어가 켜져 있다는
 * 이유로 정상 사용자가 막히거나 Redis 장애가 기능 장애로 번지면 안 된다.
 */
@ExtendWith(MockitoExtension.class)
class InviteCodeProbeThrottleTest {

    private static final String KEY = "invite_probe_fail:7";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks InviteCodeProbeThrottle throttle;

    @Test
    @DisplayName("실패가 임계 미만이면 통과한다")
    void belowThreshold_notBlocked() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(KEY)).willReturn("9");

        assertThat(throttle.isBlocked(7L)).isFalse();
    }

    @Test
    @DisplayName("실패가 임계에 도달하면 차단한다")
    void atThreshold_blocked() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(KEY)).willReturn("10");

        assertThat(throttle.isBlocked(7L)).isTrue();
    }

    @Test
    @DisplayName("기록이 없으면 통과한다")
    void noRecord_notBlocked() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.get(KEY)).willReturn(null);

        assertThat(throttle.isBlocked(7L)).isFalse();
    }

    @Test
    @DisplayName("키를 만들 때 TTL을 함께 건다 — 만료 없는 키는 영구 차단이 된다")
    void failure_createsKeyWithWindow() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);

        throttle.recordFailure(7L);

        // setIfAbsent가 TTL과 함께 키를 만든 뒤에 증가한다.
        // "증가 후 1이면 expire"로 짜면 그 사이 expire 실패가 영구 차단으로 남는다
        then(valueOps).should().setIfAbsent(KEY, "0", Duration.ofMinutes(10));
        then(valueOps).should().increment(KEY);
    }

    @Test
    @DisplayName("이미 창이 열려 있으면 TTL을 밀지 않는다 — 밀리면 차단이 끝나지 않는다")
    void laterFailure_keepsWindow() {
        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(valueOps.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .willReturn(false); // 이미 존재 — TTL 유지

        throttle.recordFailure(7L);

        then(redisTemplate).should(never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
        then(valueOps).should().increment(KEY);
    }

    @Test
    @DisplayName("Redis가 죽어도 차단하지 않는다 (fail-open)")
    void redisDown_failsOpen() {
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("down"));

        assertThat(throttle.isBlocked(7L)).isFalse();
    }

    @Test
    @DisplayName("Redis가 죽어도 기록·초기화가 예외를 던지지 않는다")
    void redisDown_recordAndResetAreSilent() {
        lenient().when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        lenient().when(redisTemplate.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> throttle.recordFailure(7L)).doesNotThrowAnyException();
        assertThatCode(() -> throttle.reset(7L)).doesNotThrowAnyException();
    }
}
