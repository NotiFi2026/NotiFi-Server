package com.notifi.server;

import com.notifi.server.domain.escalation.dto.EscalationDetailResponse;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.sensing.dto.PoseClipResponse;
import com.notifi.server.domain.sensing.dto.SensingEventSummaryResponse;
import com.notifi.server.domain.sensing.entity.ActivityClass;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 앱이 실제로 읽는 JSON 필드명을 고정한다.
 *
 * 서버 DTO는 camelCase로 쓰고 Jackson이 SNAKE_CASE로 직렬화하므로, 필드명은 코드를 봐서는
 * 확정할 수 없다. 앱은 필드명을 그대로 하드코딩하기 때문에 이름이 하나만 바뀌어도
 * 화면이 조용히 빈다 — 서비스 테스트로는 잡히지 않는 종류의 고장이다.
 *
 * DataSource·Redis 없이 Jackson 설정만 띄운다(@JsonTest).
 */
@JsonTest
@ActiveProfiles("test")
class ClientJsonContractTest {

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode json(Object value) {
        return objectMapper.valueToTree(value);
    }

    @Test
    @DisplayName("S2 감지 이벤트 목록 — 기록 탭·리플레이 진입이 읽는 필드")
    void sensingEventSummaryFieldNames() {
        JsonNode node = json(new SensingEventSummaryResponse(
                7003L,
                EventType.FALL,
                ActivityClass.FALL_FROM_STANDING,
                new BigDecimal("0.941"),
                (short) 92,
                RiskLevel.DANGER,
                Instant.parse("2026-08-12T09:22:00Z"),
                true
        ));

        assertThat(node.propertyNames()).containsExactlyInAnyOrder(
                "sensing_event_id", "event_type", "activity_class", "risk_probability",
                "risk_score", "risk_level", "detected_at", "has_replay");
        // boolean 접근자(hasReplay)는 Jackson이 is/get 규칙으로 잘라낼 여지가 있어 값까지 확인한다
        assertThat(node.get("has_replay").booleanValue()).isTrue();
        assertThat(node.get("activity_class").stringValue()).isEqualTo("FALL_FROM_STANDING");
    }

    @Test
    @DisplayName("S3 포즈클립 — frames 안쪽 키는 AI가 보낸 그대로 통과해야 한다")
    void poseClipFieldNames() {
        Map<String, Object> frames = Map.of(
                "joints", List.of("pelvis"),
                "pose_rel", List.of(List.of(List.of(0.0, 0.0, 0.0))),
                "root", List.of(List.of(0.0, 0.0, 0.0)),
                "frame_valid", List.of(true)
        );
        JsonNode node = json(new PoseClipResponse(
                5001L, 7003L, "NotiFi_AI_v1", "smpl-22", 30, 304, 10133,
                Instant.parse("2026-08-12T09:21:50Z"),
                Instant.parse("2026-08-12T09:22:00Z"),
                frames,
                null
        ));

        assertThat(node.propertyNames()).containsExactlyInAnyOrder(
                "pose_clip_id", "sensing_event_id", "model_version", "joint_schema", "fps",
                "frame_count", "duration_ms", "window_start_at", "window_end_at",
                "frames", "event_timeline");
        // Map은 자유 JSON이라 네이밍 전략이 닿지 않는다 — pose_rel이 poseRel로 바뀌면 화면이 못 그린다
        assertThat(node.get("frames").propertyNames())
                .containsExactlyInAnyOrder("joints", "pose_rel", "root", "frame_valid");
    }

    @Test
    @DisplayName("E2 응급 상세 — 응급 상세에서 리플레이를 여는 sensing_event_id")
    void escalationDetailExposesSensingEventId() {
        JsonNode node = json(new EscalationDetailResponse(
                9001L, EscalationStatus.IN_PROGRESS, null, null,
                Instant.parse("2026-08-12T09:22:00Z"), null,
                3L, "이복례", EventType.FALL, 7003L, List.of()
        ));

        assertThat(node.has("sensing_event_id")).isTrue();
        assertThat(node.get("sensing_event_id").longValue()).isEqualTo(7003L);
    }

    @Test
    @DisplayName("페이지 응답 — 앱이 content만 꺼내 쓴다")
    void pageResponseFieldNames() {
        JsonNode node = json(new PageResponse<>(List.of(), 0, 20, 3L, 1));

        assertThat(node.propertyNames()).containsExactlyInAnyOrder(
                "content", "page", "size", "total_elements", "total_pages");
    }
}
