package com.notifi.server.domain.sensing.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
}
