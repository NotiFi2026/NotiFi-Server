package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.sensing.dto.PoseClipIngestRequest;
import com.notifi.server.domain.sensing.dto.PoseClipIngestResponse;
import com.notifi.server.domain.sensing.dto.SensingEventIngestRequest;
import com.notifi.server.domain.sensing.dto.SensingEventIngestResponse;
import com.notifi.server.domain.sensing.entity.*;
import com.notifi.server.domain.sensing.exception.SensingErrorCode;
import com.notifi.server.domain.sensing.repository.PoseClipRepository;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SensingServiceTest {

    @Mock SensingEventRepository sensingEventRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock EscalationRepository escalationRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock PoseClipRepository poseClipRepository;

    @InjectMocks SensingService sensingService;

    private static final Instant DETECTED_AT = Instant.parse("2026-06-27T03:22:00Z");

    // ── DANGER → 에스컬레이션 자동 생성 ──────────────────────────────────────

    @Test
    @DisplayName("ingest: DANGER 이벤트는 에스컬레이션을 생성하고 escalation_id를 반환한다")
    void ingest_danger_triggersEscalation() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        Escalation escalation = Escalation.start(2L);
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);
        ReflectionTestUtils.setField(escalation, "id", 3L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);
        given(escalationRepository.save(any())).willReturn(escalation);

        SensingEventIngestResponse res = sensingService.ingest(dangerRequest());

        assertThat(res.sensingEventId()).isEqualTo(1L);
        assertThat(res.riskAssessmentId()).isEqualTo(2L);
        assertThat(res.escalationTriggered()).isTrue();
        assertThat(res.escalationId()).isEqualTo(3L);
        then(escalationRepository).should().save(any(Escalation.class));
    }

    // ── WARNING/SAFE → 에스컬레이션 미생성 ───────────────────────────────────

    @Test
    @DisplayName("ingest: WARNING 이벤트는 에스컬레이션을 생성하지 않는다")
    void ingest_warning_noEscalation() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);

        SensingEventIngestResponse res = sensingService.ingest(warningRequest());

        assertThat(res.escalationTriggered()).isFalse();
        assertThat(res.escalationId()).isNull();
        then(escalationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("ingest: SAFE 이벤트는 에스컬레이션을 생성하지 않는다")
    void ingest_safe_noEscalation() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);

        SensingEventIngestResponse res = sensingService.ingest(safeRequest());

        assertThat(res.escalationTriggered()).isFalse();
        then(escalationRepository).should(never()).save(any());
    }

    // ── care_target 없음 → 예외 ───────────────────────────────────────────────

    @Test
    @DisplayName("ingest: 존재하지 않는 care_target_id → CARE_TARGET_NOT_FOUND")
    void ingest_unknownCareTarget_throws() {
        given(careTargetRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> sensingService.ingest(requestFor(99L, RiskLevel.DANGER)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));

        then(sensingEventRepository).should(never()).save(any());
    }

    // ── 멱등: 동일 (careTargetId, detectedAt, eventType) 재요청 ──────────────

    @Test
    @DisplayName("ingest: 동일 이벤트 재요청 시 저장 없이 기존 ids 반환")
    void ingest_duplicate_returnsExistingIds() {
        SensingEvent existingEvent = sensingEvent();
        RiskAssessment existingRa = riskAssessment();
        Escalation existingEscalation = Escalation.start(2L);
        ReflectionTestUtils.setField(existingEvent, "id", 1L);
        ReflectionTestUtils.setField(existingRa, "id", 2L);
        ReflectionTestUtils.setField(existingEscalation, "id", 3L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.of(existingEvent));
        given(riskAssessmentRepository.findBySensingEventId(1L)).willReturn(Optional.of(existingRa));
        given(escalationRepository.findByRiskAssessmentId(2L)).willReturn(Optional.of(existingEscalation));

        SensingEventIngestResponse res = sensingService.ingest(dangerRequest());

        assertThat(res.sensingEventId()).isEqualTo(1L);
        assertThat(res.riskAssessmentId()).isEqualTo(2L);
        assertThat(res.escalationTriggered()).isTrue();
        assertThat(res.escalationId()).isEqualTo(3L);
        then(sensingEventRepository).should(never()).save(any());
        then(riskAssessmentRepository).should(never()).save(any());
        then(escalationRepository).should(never()).save(any());
    }

    // ── I5: ingestPoseClip ────────────────────────────────────────────────────

    @Test
    @DisplayName("ingestPoseClip: 신규 클립을 저장하고 pose_clip_id를 반환한다")
    void ingestPoseClip_new_savesAndReturns() {
        PoseClip clip = poseClip();
        ReflectionTestUtils.setField(clip, "id", 10L);

        given(sensingEventRepository.existsById(1L)).willReturn(true);
        given(poseClipRepository.findBySensingEventId(1L)).willReturn(Optional.empty());
        given(poseClipRepository.save(any())).willReturn(clip);

        PoseClipIngestResponse res = sensingService.ingestPoseClip(1L, poseClipRequest());

        assertThat(res.poseClipId()).isEqualTo(10L);
        assertThat(res.sensingEventId()).isEqualTo(1L);
        then(poseClipRepository).should().save(any(PoseClip.class));
    }

    @Test
    @DisplayName("ingestPoseClip: 동일 sensing_event_id 재요청 시 기존 id를 반환하고 저장하지 않는다")
    void ingestPoseClip_duplicate_returnsExistingId() {
        PoseClip existing = poseClip();
        ReflectionTestUtils.setField(existing, "id", 10L);

        given(sensingEventRepository.existsById(1L)).willReturn(true);
        given(poseClipRepository.findBySensingEventId(1L)).willReturn(Optional.of(existing));

        PoseClipIngestResponse res = sensingService.ingestPoseClip(1L, poseClipRequest());

        assertThat(res.poseClipId()).isEqualTo(10L);
        assertThat(res.sensingEventId()).isEqualTo(1L);
        then(poseClipRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("ingestPoseClip: 존재하지 않는 sensing_event_id → SENSING_EVENT_NOT_FOUND")
    void ingestPoseClip_unknownEvent_throws() {
        given(sensingEventRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> sensingService.ingestPoseClip(99L, poseClipRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(SensingErrorCode.SENSING_EVENT_NOT_FOUND));

        then(poseClipRepository).should(never()).save(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SensingEventIngestRequest dangerRequest() {
        return requestFor(1L, RiskLevel.DANGER);
    }

    private SensingEventIngestRequest warningRequest() {
        return requestFor(1L, RiskLevel.WARNING);
    }

    private SensingEventIngestRequest safeRequest() {
        return requestFor(1L, RiskLevel.SAFE);
    }

    private SensingEventIngestRequest requestFor(Long careTargetId, RiskLevel level) {
        return new SensingEventIngestRequest(
                careTargetId, null, EventType.FALL,
                null, null, null, null,
                "v0.1", null, DETECTED_AT,
                (short) 85, level, null
        );
    }

    private SensingEvent sensingEvent() {
        return SensingEvent.create(1L, null, EventType.FALL,
                null, null, null, null, "v0.1", null, DETECTED_AT);
    }

    private RiskAssessment riskAssessment() {
        return RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", DETECTED_AT);
    }

    private PoseClip poseClip() {
        return PoseClip.of(1L, "v0.1", "13-point", (short) 10, 300, 30000,
                DETECTED_AT, DETECTED_AT.plusSeconds(30),
                Map.of("frames", "data"), null);
    }

    private PoseClipIngestRequest poseClipRequest() {
        return new PoseClipIngestRequest(
                "v0.1", "13-point", 10, 300, 30000,
                DETECTED_AT, DETECTED_AT.plusSeconds(30),
                Map.of("frames", "data"), null
        );
    }
}
