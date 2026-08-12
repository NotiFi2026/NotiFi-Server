package com.notifi.server.domain.sensing.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI v1 모델의 17개 행동 클래스 — event_type의 세부 분류.
 * safe 9종 → NORMAL, warning 3종 → ANOMALY, danger 5종 → FALL 로 매핑되어 적재된다.
 */
public enum ActivityClass {
    // safe (9)
    WALKING, STANDING_STILL, SITTING_STILL, LYING_STILL,
    LIE_TO_STAND, STAND_TO_LIE_NORMAL, ABSENCE, SIT_TO_STAND, STAND_TO_SIT,
    // warning (3)
    UNSTABLE_WALKING, STUMBLE_RECOVER, BED_EXIT_FAILED,
    // danger (5)
    FALL_FROM_STANDING, FALL_WHILE_WALKING, BED_EXIT_FALL, BED_FALL, CHAIR_EXIT_FALL;

    private static final Logger log = LoggerFactory.getLogger(ActivityClass.class);

    /**
     * 관대 바인딩 — 선택 필드인 activity_class가 응급 이벤트 전체를 400으로 막지 않도록,
     * 미지의 값·소문자는 대문자 정규화 시도 후 실패 시 WARN 로그 + null로 수용한다 (DB CHECK도 null 허용).
     */
    @JsonCreator
    public static ActivityClass from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[I1] 알 수 없는 activity_class 값 무시: {}", value);
            return null;
        }
    }
}
