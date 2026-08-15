package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
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
import org.hibernate.exception.ConstraintViolationException;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    @DisplayName("ingest: 이미 진행 중인 에스컬레이션이 있으면 새로 만들지 않고 기존 건을 가리킨다")
    void ingest_danger_whileEscalationInProgress_doesNotCreateAnother() {
        // 낙상 한 번에 겹치는 윈도가 여러 개 나온다. 그때마다 새로 만들면 음성 확인이
        // 처음부터 다시 시작되고("괜찮다고 했는데 또 물어본다") 119 단계도 중복으로 걸린다.
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);

        Escalation ongoing = Escalation.start(99L);
        ReflectionTestUtils.setField(ongoing, "id", 7L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);
        given(escalationRepository.findOngoingSince(
                eq(1L), eq(EscalationStatus.IN_PROGRESS), any(), any()))
                .willReturn(List.of(ongoing));

        SensingEventIngestResponse res = sensingService.ingest(dangerRequest());

        // 이벤트·위험도는 그대로 적재된다 — 기록을 버리는 게 아니라 대응만 겹치지 않게 한다
        assertThat(res.sensingEventId()).isEqualTo(1L);
        assertThat(res.riskAssessmentId()).isEqualTo(2L);
        // AI 에이전트는 이 플래그로 새 대응 흐름을 시작할지 판단한다
        assertThat(res.escalationTriggered()).isFalse();
        assertThat(res.escalationId()).isEqualTo(7L);
        then(escalationRepository).should(never()).save(any(Escalation.class));
    }

    @Test
    @DisplayName("ingest: 앞 건이 해소된 뒤의 DANGER는 새 에스컬레이션을 만든다")
    void ingest_danger_afterResolved_createsNewEscalation() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        Escalation created = Escalation.start(2L);
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);
        ReflectionTestUtils.setField(created, "id", 8L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);
        given(escalationRepository.findOngoingSince(
                eq(1L), eq(EscalationStatus.IN_PROGRESS), any(), any()))
                .willReturn(List.of());   // 진행 중 없음
        given(escalationRepository.save(any())).willReturn(created);

        SensingEventIngestResponse res = sensingService.ingest(dangerRequest());

        assertThat(res.escalationTriggered()).isTrue();
        assertThat(res.escalationId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("ingest: 재사용 판단은 최근 건만 본다 — 오래된 미해소 건이 새 응급을 막으면 안 된다")
    void ingest_danger_onlyReusesRecentEscalation() {
        // 119까지 간 에스컬레이션은 사람이 앱에서 닫을 때까지 IN_PROGRESS로 남는다.
        // 기한 없이 "대응 중"으로 보면 몇 시간 뒤의 진짜 낙상이 조용히 묻힌다.
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        Escalation created = Escalation.start(2L);
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);
        ReflectionTestUtils.setField(created, "id", 9L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);
        // 오래된 건은 조회 자체에서 빠진다(startedAt >= since) — 저장소가 빈 목록을 준다
        given(escalationRepository.findOngoingSince(
                eq(1L), eq(EscalationStatus.IN_PROGRESS), any(), any()))
                .willReturn(List.of());
        given(escalationRepository.save(any())).willReturn(created);

        SensingEventIngestResponse res = sensingService.ingest(dangerRequest());

        assertThat(res.escalationTriggered()).isTrue();
        assertThat(res.escalationId()).isEqualTo(9L);

        // 조회 기준 시각이 "지금"보다 과거여야 한다 — 창이 0이면 항상 새로 만들고,
        // 미래면 아무것도 못 찾아 dedup이 죽는다
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        then(escalationRepository).should().findOngoingSince(
                eq(1L), eq(EscalationStatus.IN_PROGRESS), since.capture(), any());
        assertThat(since.getValue()).isBefore(Instant.now());
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
    @DisplayName("ingest: 동시 중복 경합(유니크 위반) → SENSING_EVENT_ALREADY_EXISTS 409")
    void ingest_concurrentDuplicate_conflict() {
        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willThrow(new DataIntegrityViolationException("dup",
                new ConstraintViolationException("dup", null, "uq_sensing_event_identity")));

        assertThatThrownBy(() -> sensingService.ingest(dangerRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SensingErrorCode.SENSING_EVENT_ALREADY_EXISTS);
        then(riskAssessmentRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("ingest: 유니크 외 제약 위반(FK 등)은 409로 오보고하지 않고 그대로 전파")
    void ingest_otherConstraintViolation_rethrown() {
        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.FALL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willThrow(new DataIntegrityViolationException("fk",
                new ConstraintViolationException("fk", null, "tb_sensing_event_device_id_fkey")));

        assertThatThrownBy(() -> sensingService.ingest(dangerRequest()))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    // ── AI v1 계약: activity_class 저장 + detected_at ms 절삭 ─────────────────

    @Test
    @DisplayName("ingest: activity_class가 저장 엔티티에 반영되고 detected_at은 ms로 절삭된다")
    void ingest_persistsActivityClass_andTruncatesDetectedAtToMillis() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);

        Instant nanoPrecision = Instant.parse("2026-06-27T03:22:00.123456789Z");
        Instant msPrecision = Instant.parse("2026-06-27T03:22:00.123Z");

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, msPrecision, EventType.NORMAL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);

        sensingService.ingest(new SensingEventIngestRequest(
                1L, null, EventType.NORMAL, ActivityClass.WALKING,
                null, null, null, null,
                "v0.1", null, nanoPrecision,
                (short) 2, RiskLevel.SAFE, null
        ));

        ArgumentCaptor<SensingEvent> captor = ArgumentCaptor.forClass(SensingEvent.class);
        then(sensingEventRepository).should().save(captor.capture());
        assertThat(captor.getValue().getActivityClass()).isEqualTo(ActivityClass.WALKING);
        assertThat(captor.getValue().getDetectedAt()).isEqualTo(msPrecision);
    }

    @Test
    @DisplayName("ingest: event_type·activity_class 모순 조합도 거부 없이 저장된다 (WARN 관측만)")
    void ingest_mismatchedActivityClass_stillPersists() {
        SensingEvent event = sensingEvent();
        RiskAssessment ra = riskAssessment();
        ReflectionTestUtils.setField(event, "id", 1L);
        ReflectionTestUtils.setField(ra, "id", 2L);

        given(careTargetRepository.existsById(1L)).willReturn(true);
        given(sensingEventRepository.findByCareTargetIdAndDetectedAtAndEventType(
                1L, DETECTED_AT, EventType.NORMAL)).willReturn(Optional.empty());
        given(sensingEventRepository.save(any())).willReturn(event);
        given(riskAssessmentRepository.save(any())).willReturn(ra);

        sensingService.ingest(new SensingEventIngestRequest(
                1L, null, EventType.NORMAL, ActivityClass.FALL_FROM_STANDING,
                null, null, null, null,
                "v0.1", null, DETECTED_AT,
                (short) 2, RiskLevel.SAFE, null
        ));

        ArgumentCaptor<SensingEvent> captor = ArgumentCaptor.forClass(SensingEvent.class);
        then(sensingEventRepository).should().save(captor.capture());
        assertThat(captor.getValue().getActivityClass()).isEqualTo(ActivityClass.FALL_FROM_STANDING);
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
    @DisplayName("ingestPoseClip: 신규 클립을 저장하고 pose_clip_id를 반환한다 — fps·frameCount·window 매핑 검증")
    void ingestPoseClip_new_savesAndReturns() {
        PoseClip clip = poseClip();
        ReflectionTestUtils.setField(clip, "id", 10L);

        given(sensingEventRepository.existsById(1L)).willReturn(true);
        given(poseClipRepository.findBySensingEventId(1L)).willReturn(Optional.empty());
        given(poseClipRepository.save(any())).willReturn(clip);

        PoseClipIngestResponse res = sensingService.ingestPoseClip(1L, poseClipRequest());

        assertThat(res.poseClipId()).isEqualTo(10L);
        assertThat(res.sensingEventId()).isEqualTo(1L);

        ArgumentCaptor<PoseClip> captor = ArgumentCaptor.forClass(PoseClip.class);
        then(poseClipRepository).should().save(captor.capture());
        PoseClip saved = captor.getValue();
        assertThat(saved.getFps()).isEqualTo((short) 10);
        assertThat(saved.getFrameCount()).isEqualTo(300);
        assertThat(saved.getWindowStartAt()).isEqualTo(DETECTED_AT);
        assertThat(saved.getWindowEndAt()).isEqualTo(DETECTED_AT.plusSeconds(30));
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
                careTargetId, null, EventType.FALL, null,
                null, null, null, null,
                "v0.1", null, DETECTED_AT,
                (short) 85, level, null
        );
    }

    private SensingEvent sensingEvent() {
        return SensingEvent.create(1L, null, EventType.FALL, null,
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
