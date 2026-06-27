package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.entity.NodeRole;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.domain.sensing.dto.CareTargetStatusResponse;
import com.notifi.server.domain.sensing.dto.SensingEventSummaryResponse;
import com.notifi.server.domain.sensing.entity.*;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SensingQueryServiceTest {

    @Mock SensingEventRepository sensingEventRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock CareRelationshipRepository careRelationshipRepository;
    @Mock CareTargetRepository careTargetRepository;

    @InjectMocks SensingQueryService sensingQueryService;

    private static final Instant DETECTED_AT = Instant.parse("2026-06-27T03:22:00Z");

    // ── S1: getStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus: 최신 이벤트·위험도·디바이스 매핑, todayMetrics·activeEscalation null")
    void getStatus_withLatestEvent_mapsFieldsCorrectly() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDesc(45L))
                .willReturn(Optional.of(event));

        RiskAssessment ra = RiskAssessment.of(1L, (short) 80, RiskLevel.WARNING, null, "v0.1", DETECTED_AT);
        given(riskAssessmentRepository.findBySensingEventId(1L)).willReturn(Optional.of(ra));

        Device device = Device.create(45L, "AA:BB:CC", "거실", null, NodeRole.RECEIVER, null);
        ReflectionTestUtils.setField(device, "id", 10L);
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of(device));

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.careTargetId()).isEqualTo(45L);
        assertThat(result.currentRiskLevel()).isEqualTo(RiskLevel.WARNING);
        assertThat(result.lastActivityAt()).isEqualTo(DETECTED_AT);
        assertThat(result.devices()).hasSize(1);
        assertThat(result.devices().get(0).deviceId()).isEqualTo(10L);
        assertThat(result.devices().get(0).room()).isEqualTo("거실");
        assertThat(result.todayMetrics()).isNull();
        assertThat(result.activeEscalation()).isNull();
    }

    @Test
    @DisplayName("getStatus: 이벤트 없으면 currentRiskLevel·lastActivityAt null")
    void getStatus_noEvents_returnsNullRisk() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDesc(45L))
                .willReturn(Optional.empty());
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.currentRiskLevel()).isNull();
        assertThat(result.lastActivityAt()).isNull();
        assertThat(result.devices()).isEmpty();
    }

    @Test
    @DisplayName("getStatus: 관계 없고 노인 존재 → ACCESS_DENIED")
    void getStatus_noRelationship_targetExists_accessDenied() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(false);
        given(careTargetRepository.existsById(45L)).willReturn(true);

        assertThatThrownBy(() -> sensingQueryService.getStatus(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("getStatus: 노인 없음 → CARE_TARGET_NOT_FOUND")
    void getStatus_targetNotFound() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 99L)).willReturn(false);
        given(careTargetRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> sensingQueryService.getStatus(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));
    }

    // ── S2: getEvents ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvents: 위험도 일괄 조인 → risk_score·risk_level 매핑, has_replay=false")
    void getEvents_withRiskAssessment_mapsAllFields() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findEvents(eq(45L), eq(EventType.FALL), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(event)));

        RiskAssessment ra = RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", DETECTED_AT);
        given(riskAssessmentRepository.findBySensingEventIdIn(List.of(1L))).willReturn(List.of(ra));

        PageResponse<SensingEventSummaryResponse> result = sensingQueryService.getEvents(
                1L, 45L, EventType.FALL, null, null, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        SensingEventSummaryResponse summary = result.content().get(0);
        assertThat(summary.sensingEventId()).isEqualTo(1L);
        assertThat(summary.eventType()).isEqualTo(EventType.FALL);
        assertThat(summary.riskScore()).isEqualTo((short) 85);
        assertThat(summary.riskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(summary.hasReplay()).isFalse();
    }

    @Test
    @DisplayName("getEvents: 위험도 없는 이벤트 → riskScore·riskLevel null, has_replay=false")
    void getEvents_noRiskAssessment_nullsRiskFields() {
        given(careRelationshipRepository.existsByUserIdAndCareTargetId(1L, 45L)).willReturn(true);

        SensingEvent event = SensingEvent.create(45L, null, EventType.NORMAL,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 2L);
        given(sensingEventRepository.findEvents(eq(45L), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(event)));
        given(riskAssessmentRepository.findBySensingEventIdIn(List.of(2L))).willReturn(List.of());

        PageResponse<SensingEventSummaryResponse> result = sensingQueryService.getEvents(
                1L, 45L, null, null, null, Pageable.unpaged());

        SensingEventSummaryResponse summary = result.content().get(0);
        assertThat(summary.riskScore()).isNull();
        assertThat(summary.riskLevel()).isNull();
        assertThat(summary.hasReplay()).isFalse();
    }
}
