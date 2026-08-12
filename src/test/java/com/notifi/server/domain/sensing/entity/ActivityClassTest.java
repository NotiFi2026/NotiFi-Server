package com.notifi.server.domain.sensing.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityClassTest {

    // 테스트 프로파일은 Flyway를 실행하지 않으므로, enum과 V11 CHECK 제약의
    // 17종 이중 하드코딩 드리프트(→ 런타임 500)는 이 패리티 테스트가 유일한 CI 가드다.
    @Test
    @DisplayName("V11 CHECK 제약이 enum 전원을 포함한다 (드리프트 가드)")
    void migrationCheckContainsEveryEnumValue() throws IOException {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/db/migration/V11__add_sensing_activity_class.sql")) {
            assertThat(in).as("V11 마이그레이션 파일 존재").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (ActivityClass ac : ActivityClass.values()) {
            assertThat(sql).as("V11 CHECK에 %s 포함", ac.name()).contains("'" + ac.name() + "'");
        }
    }

    @Test
    @DisplayName("관대 바인딩: 소문자·공백은 대문자 정규화로 수용")
    void from_lowercase_normalized() {
        assertThat(ActivityClass.from("walking")).isEqualTo(ActivityClass.WALKING);
        assertThat(ActivityClass.from(" fall_from_standing ")).isEqualTo(ActivityClass.FALL_FROM_STANDING);
    }

    @Test
    @DisplayName("관대 바인딩: 미지의 값·null·blank는 null (요청 전체를 400으로 막지 않음)")
    void from_unknownOrEmpty_returnsNull() {
        assertThat(ActivityClass.from("FLYING")).isNull();
        assertThat(ActivityClass.from(null)).isNull();
        assertThat(ActivityClass.from("  ")).isNull();
    }

    @Test
    @DisplayName("관대 바인딩: 터키어 로케일에서도 i→İ 변환 없이 정상 매칭 (Locale.ROOT 회귀 가드)")
    void from_turkishLocale_stillResolves() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(ActivityClass.from("sitting_still")).isEqualTo(ActivityClass.SITTING_STILL);
            assertThat(ActivityClass.from("lying_still")).isEqualTo(ActivityClass.LYING_STILL);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("expectedEventType: safe 9종→NORMAL, warning 3종→ANOMALY, danger 5종→FALL")
    void expectedEventType_mapsCategories() {
        assertThat(Arrays.stream(ActivityClass.values())
                .filter(ac -> ac.expectedEventType() == EventType.NORMAL)).hasSize(9);
        assertThat(Arrays.stream(ActivityClass.values())
                .filter(ac -> ac.expectedEventType() == EventType.ANOMALY)).hasSize(3);
        assertThat(Arrays.stream(ActivityClass.values())
                .filter(ac -> ac.expectedEventType() == EventType.FALL)).hasSize(5);
        assertThat(ActivityClass.WALKING.expectedEventType()).isEqualTo(EventType.NORMAL);
        assertThat(ActivityClass.UNSTABLE_WALKING.expectedEventType()).isEqualTo(EventType.ANOMALY);
        assertThat(ActivityClass.FALL_FROM_STANDING.expectedEventType()).isEqualTo(EventType.FALL);
    }
}
