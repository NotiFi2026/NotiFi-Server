package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.entity.NodeRole;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepStatus;
import com.notifi.server.domain.escalation.entity.StepType;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.sensing.dto.CareTargetStatusResponse;
import com.notifi.server.domain.sensing.dto.PoseClipResponse;
import com.notifi.server.domain.sensing.dto.SensingEventSummaryResponse;
import com.notifi.server.domain.sensing.entity.*;
import com.notifi.server.domain.sensing.exception.SensingErrorCode;
import com.notifi.server.domain.sensing.repository.PoseClipRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class SensingQueryServiceTest {

    @Mock SensingEventRepository sensingEventRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock PoseClipRepository poseClipRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock EscalationRepository escalationRepository;
    @Mock EscalationStepRepository escalationStepRepository;
    @Mock CareTargetAccessValidator accessValidator;

    @InjectMocks SensingQueryService sensingQueryService;

    private static final Instant DETECTED_AT = Instant.parse("2026-06-27T03:22:00Z");

    // ── S1: getStatus ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus: 최신 이벤트·위험도·디바이스 매핑, todayMetrics·activeEscalation null")
    void getStatus_withLatestEvent_mapsFieldsCorrectly() {

        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
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
    @DisplayName("getStatus: 진행 중 에스컬레이션 있으면 active_escalation 요약 반환")
    void getStatus_withActiveEscalation_mapsSummary() {
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
                .willReturn(Optional.empty());
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 30L);
        given(escalationRepository.findByCareTargetIdAndStatus(
                eq(45L), eq(EscalationStatus.IN_PROGRESS), any()))
                .willReturn(List.of(escalation));

        EscalationStep latestStep = EscalationStep.record(30L, StepType.GUARDIAN_NOTIFY, (short) 2,
                StepStatus.EXECUTED, DETECTED_AT, null, null);
        given(escalationStepRepository.findFirstByEscalationIdOrderByStepOrderDesc(30L))
                .willReturn(Optional.of(latestStep));

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.activeEscalation()).isNotNull();
        assertThat(result.activeEscalation().escalationId()).isEqualTo(30L);
        assertThat(result.activeEscalation().currentStepType()).isEqualTo(StepType.GUARDIAN_NOTIFY);
        assertThat(result.activeEscalation().startedAt()).isNotNull();
    }

    @Test
    @DisplayName("getStatus: 응급 진행 중이면 최신 이벤트가 SAFE여도 currentRiskLevel=DANGER")
    void getStatus_activeEscalation_promotesRiskToDanger() {
        // 낙상 직후 사람이 조금만 움직여도 SAFE 이벤트가 곧바로 들어온다(실측: 18초 뒤 WALKING).
        // 최신 이벤트만 보면 응급이 도는 동안 대시보드가 초록으로 돌아가, 같은 화면에 뜬
        // 응급 콘솔과 "정상이에요"가 동시에 보인다.
        SensingEvent event = SensingEvent.create(45L, null, EventType.NORMAL, null,
                null, null, null, null, "v1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
                .willReturn(Optional.of(event));
        given(riskAssessmentRepository.findBySensingEventId(1L))
                .willReturn(Optional.of(RiskAssessment.of(1L, (short) 5, RiskLevel.SAFE, null, "v1", DETECTED_AT)));
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 30L);
        given(escalationRepository.findByCareTargetIdAndStatus(
                eq(45L), eq(EscalationStatus.IN_PROGRESS), any()))
                .willReturn(List.of(escalation));
        given(escalationStepRepository.findFirstByEscalationIdOrderByStepOrderDesc(30L))
                .willReturn(Optional.empty());

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.currentRiskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(result.activeEscalation()).isNotNull();
    }

    @Test
    @DisplayName("getStatus: 응급이 없으면 승격하지 않는다 — 최신 이벤트 값 그대로")
    void getStatus_noEscalation_keepsLatestEventRisk() {
        // 승격만 하고 강등도 하지 않는다. DANGER 이벤트는 에스컬레이션 없이도 DANGER다.
        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, null,
                null, null, null, null, "v1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
                .willReturn(Optional.of(event));
        given(riskAssessmentRepository.findBySensingEventId(1L))
                .willReturn(Optional.of(RiskAssessment.of(1L, (short) 90, RiskLevel.DANGER, null, "v1", DETECTED_AT)));
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.currentRiskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(result.activeEscalation()).isNull();
    }

    @Test
    @DisplayName("getStatus: 이벤트 없으면 currentRiskLevel·lastActivityAt null")
    void getStatus_noEvents_returnsNullRisk() {
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
                .willReturn(Optional.empty());
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        CareTargetStatusResponse result = sensingQueryService.getStatus(1L, 45L);

        assertThat(result.currentRiskLevel()).isNull();
        assertThat(result.lastActivityAt()).isNull();
        assertThat(result.devices()).isEmpty();
    }

    @Test
    @DisplayName("getStatus: 본인·관계 검증 통과(노인 본인 포함) → requireRelationshipOrSelf 위임")
    void getStatus_selfAccess_delegatesToOrSelfGuard() {
        given(sensingEventRepository.findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(45L))
                .willReturn(Optional.empty());
        given(deviceRepository.findByCareTargetIdOrderByRegisteredAtAsc(45L)).willReturn(List.of());

        sensingQueryService.getStatus(9L, 45L);

        then(accessValidator).should().requireRelationshipOrSelf(9L, 45L);
    }

    @Test
    @DisplayName("getStatus: 관계도 본인도 아님(노인 존재) → ACCESS_DENIED")
    void getStatus_noRelationship_targetExists_accessDenied() {
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requireRelationshipOrSelf(1L, 45L);

        assertThatThrownBy(() -> sensingQueryService.getStatus(1L, 45L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("getStatus: 노인 없음 → CARE_TARGET_NOT_FOUND")
    void getStatus_targetNotFound() {
        willThrow(new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND))
                .given(accessValidator).requireRelationshipOrSelf(1L, 99L);

        assertThatThrownBy(() -> sensingQueryService.getStatus(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));
    }

    // ── S2: getEvents ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvents: 포즈클립 없는 이벤트 → has_replay=false, activity_class 매핑")
    void getEvents_withRiskAssessment_noClip_hasReplayFalse() {

        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, ActivityClass.FALL_FROM_STANDING,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findEvents(eq(45L), eq(EventType.FALL), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(event)));

        RiskAssessment ra = RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", DETECTED_AT);
        given(riskAssessmentRepository.findBySensingEventIdIn(List.of(1L))).willReturn(List.of(ra));
        given(poseClipRepository.findExistingSensingEventIds(List.of(1L))).willReturn(List.of());

        PageResponse<SensingEventSummaryResponse> result = sensingQueryService.getEvents(
                1L, 45L, EventType.FALL, null, null, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        SensingEventSummaryResponse summary = result.content().get(0);
        assertThat(summary.sensingEventId()).isEqualTo(1L);
        assertThat(summary.activityClass()).isEqualTo(ActivityClass.FALL_FROM_STANDING);
        assertThat(summary.riskScore()).isEqualTo((short) 85);
        assertThat(summary.riskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(summary.hasReplay()).isFalse();
    }

    @Test
    @DisplayName("getEvents: 포즈클립 있는 이벤트 → has_replay=true")
    void getEvents_withPoseClip_hasReplayTrue() {

        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 1L);
        given(sensingEventRepository.findEvents(eq(45L), eq(EventType.FALL), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(event)));

        RiskAssessment ra = RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", DETECTED_AT);
        given(riskAssessmentRepository.findBySensingEventIdIn(List.of(1L))).willReturn(List.of(ra));
        given(poseClipRepository.findExistingSensingEventIds(List.of(1L))).willReturn(List.of(1L));

        PageResponse<SensingEventSummaryResponse> result = sensingQueryService.getEvents(
                1L, 45L, EventType.FALL, null, null, Pageable.unpaged());

        assertThat(result.content().get(0).hasReplay()).isTrue();
    }

    @Test
    @DisplayName("getEvents: 위험도 없는 이벤트 → riskScore·riskLevel null, has_replay=false")
    void getEvents_noRiskAssessment_nullsRiskFields() {

        SensingEvent event = SensingEvent.create(45L, null, EventType.NORMAL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 2L);
        given(sensingEventRepository.findEvents(eq(45L), any(), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(event)));
        given(riskAssessmentRepository.findBySensingEventIdIn(List.of(2L))).willReturn(List.of());
        given(poseClipRepository.findExistingSensingEventIds(List.of(2L))).willReturn(List.of());

        PageResponse<SensingEventSummaryResponse> result = sensingQueryService.getEvents(
                1L, 45L, null, null, null, Pageable.unpaged());

        SensingEventSummaryResponse summary = result.content().get(0);
        assertThat(summary.riskScore()).isNull();
        assertThat(summary.riskLevel()).isNull();
        assertThat(summary.hasReplay()).isFalse();
    }

    // ── S3: getPoseClip ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getPoseClip: 정상 → PoseClipResponse 반환·필드 매핑 일치")
    void getPoseClip_success_returnsResponse() {
        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 10L);
        given(sensingEventRepository.findById(10L)).willReturn(Optional.of(event));

        Instant start = Instant.parse("2026-06-27T03:22:00Z");
        Instant end   = Instant.parse("2026-06-27T03:22:05Z");
        Map<String, Object> frames = Map.of("joints", List.of("head"));
        PoseClip clip = PoseClip.of(10L, "csi-pose-v0.1", "13-point",
                (short) 30, 150, 5000, start, end, frames, null);
        ReflectionTestUtils.setField(clip, "id", 7L);
        given(poseClipRepository.findBySensingEventId(10L)).willReturn(Optional.of(clip));

        PoseClipResponse result = sensingQueryService.getPoseClip(1L, 10L);

        assertThat(result.poseClipId()).isEqualTo(7L);
        assertThat(result.sensingEventId()).isEqualTo(10L);
        assertThat(result.modelVersion()).isEqualTo("csi-pose-v0.1");
        assertThat(result.jointSchema()).isEqualTo("13-point");
        assertThat(result.fps()).isEqualTo(30);
        assertThat(result.frameCount()).isEqualTo(150);
        assertThat(result.durationMs()).isEqualTo(5000);
        assertThat(result.windowStartAt()).isEqualTo(start);
        assertThat(result.windowEndAt()).isEqualTo(end);
        assertThat(result.frames()).isEqualTo(frames);
        assertThat(result.eventTimeline()).isNull();
    }

    @Test
    @DisplayName("getPoseClip: 이벤트 없음 → SENSING_EVENT_NOT_FOUND")
    void getPoseClip_eventNotFound() {
        given(sensingEventRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sensingQueryService.getPoseClip(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SensingErrorCode.SENSING_EVENT_NOT_FOUND));
    }

    @Test
    @DisplayName("getPoseClip: 관계 없고 노인 존재 → ACCESS_DENIED")
    void getPoseClip_noRelationship_accessDenied() {
        SensingEvent event = SensingEvent.create(45L, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 10L);
        given(sensingEventRepository.findById(10L)).willReturn(Optional.of(event));
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requireRelationshipOrSelf(1L, 45L);

        assertThatThrownBy(() -> sensingQueryService.getPoseClip(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("getPoseClip: 클립 없음(NORMAL 이벤트 등) → POSE_CLIP_NOT_FOUND")
    void getPoseClip_clipNotFound() {
        SensingEvent event = SensingEvent.create(45L, null, EventType.NORMAL, null,
                null, null, null, null, "v0.1", null, DETECTED_AT);
        ReflectionTestUtils.setField(event, "id", 10L);
        given(sensingEventRepository.findById(10L)).willReturn(Optional.of(event));
        given(poseClipRepository.findBySensingEventId(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sensingQueryService.getPoseClip(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SensingErrorCode.POSE_CLIP_NOT_FOUND));
    }
}
