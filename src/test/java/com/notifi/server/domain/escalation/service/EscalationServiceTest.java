package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.entity.Gender;
import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.escalation.dto.EscalationDetailResponse;
import com.notifi.server.domain.escalation.dto.EscalationResolveRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepResponse;
import com.notifi.server.domain.escalation.dto.EscalationSummaryResponse;
import com.notifi.server.domain.escalation.entity.*;
import com.notifi.server.domain.escalation.event.GuardianNotifyRequestedEvent;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock EscalationRepository escalationRepository;
    @Mock EscalationStepRepository escalationStepRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock SensingEventRepository sensingEventRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock CareTargetAccessValidator accessValidator;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks EscalationService escalationService;

    private static final Instant EXECUTED_AT = Instant.parse("2026-06-28T03:22:00Z");
    private static final Long USER_ID = 77L;
    private static final Long CARE_TARGET_ID = 99L;

    // ═══════════════════════════════════════════════════════════════════════════
    // recordStep (I2) 기존 테스트
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("recordStep: VOICE_CHECK 신규(EXECUTED) → step 저장 + 노인 앱 음성확인 푸시")
    void recordStep_voiceCheck_new_dispatchesVoiceCheck() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep step = voiceCheckStep();
        ReflectionTestUtils.setField(step, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);
        given(riskAssessmentRepository.findById(1L)).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(5L)).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));

        EscalationStepResponse res = escalationService.recordStep(10L, voiceCheckRequest());

        assertThat(res.stepId()).isEqualTo(100L);
        assertThat(res.stepType()).isEqualTo(StepType.VOICE_CHECK);
        then(eventPublisher).should().publishEvent(
                new VoiceCheckRequestedEvent(100L, 10L, CARE_TARGET_ID));
        then(eventPublisher).should(never()).publishEvent(any(GuardianNotifyRequestedEvent.class));
    }

    @Test
    @DisplayName("recordStep: VOICE_CHECK 신규(PENDING) → step 저장 + 노인 앱 음성확인 푸시")
    void recordStep_voiceCheck_pending_dispatchesVoiceCheck() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep step = voiceCheckStep();
        ReflectionTestUtils.setField(step, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);
        given(riskAssessmentRepository.findById(1L)).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(5L)).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));

        escalationService.recordStep(10L, voiceCheckRequest(StepStatus.PENDING));

        then(eventPublisher).should().publishEvent(
                new VoiceCheckRequestedEvent(100L, 10L, CARE_TARGET_ID));
    }

    @Test
    @DisplayName("recordStep: VOICE_CHECK 첫 기록이 SKIPPED(사후 기록) → 푸시 미발송")
    void recordStep_voiceCheck_skipped_noDispatch() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep step = voiceCheckStep();
        ReflectionTestUtils.setField(step, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);

        escalationService.recordStep(10L, voiceCheckRequest(StepStatus.SKIPPED));

        then(eventPublisher).should(never()).publishEvent(any(VoiceCheckRequestedEvent.class));
    }

    @Test
    @DisplayName("recordStep: GUARDIAN_NOTIFY 신규 + guardian_message → FCM 발송 트리거")
    void recordStep_guardianNotify_new_triggersNotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep step = guardianNotifyStep();
        ReflectionTestUtils.setField(step, "id", 101L);

        RiskAssessment ra = RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", EXECUTED_AT);
        ReflectionTestUtils.setField(ra, "id", 1L);
        ReflectionTestUtils.setField(ra, "sensingEventId", 5L);

        SensingEvent event = SensingEvent.create(99L, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, EXECUTED_AT);
        ReflectionTestUtils.setField(event, "id", 5L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.GUARDIAN_NOTIFY))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);
        given(riskAssessmentRepository.findById(1L)).willReturn(Optional.of(ra));
        given(sensingEventRepository.findById(5L)).willReturn(Optional.of(event));

        escalationService.recordStep(10L, guardianNotifyRequest());

        ArgumentCaptor<GuardianNotifyRequestedEvent> captor =
                ArgumentCaptor.forClass(GuardianNotifyRequestedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        GuardianNotifyRequestedEvent published = captor.getValue();
        assertThat(published.escalationStepId()).isEqualTo(101L);
        assertThat(published.escalationId()).isEqualTo(10L);
        assertThat(published.careTargetId()).isEqualTo(99L);
        assertThat(published.guardianMessage()).isNotNull();
    }

    @Test
    @DisplayName("recordStep: 동일 step_type 재요청 → updateProgress 호출, 알림 미발송, save 미호출")
    void recordStep_duplicate_updatesProgress_noNotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = voiceCheckStep();
        ReflectionTestUtils.setField(existing, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.of(existing));

        escalationService.recordStep(10L, voiceCheckRequest());

        then(escalationStepRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any(GuardianNotifyRequestedEvent.class));
        then(eventPublisher).should(never()).publishEvent(any(VoiceCheckRequestedEvent.class));
        assertThat(existing.getStatus()).isEqualTo(StepStatus.EXECUTED);
    }

    @Test
    @DisplayName("recordStep: 존재하지 않는 escalation_id → ESCALATION_NOT_FOUND")
    void recordStep_unknownEscalation_throws() {
        given(escalationRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> escalationService.recordStep(99L, voiceCheckRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_NOT_FOUND));

        then(escalationStepRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("recordStep: GUARDIAN_NOTIFY 재요청 → FCM 재발송 없음")
    void recordStep_guardianNotify_duplicate_noRenotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = guardianNotifyStep();
        ReflectionTestUtils.setField(existing, "id", 101L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.GUARDIAN_NOTIFY))
                .willReturn(Optional.of(existing));

        escalationService.recordStep(10L, guardianNotifyRequest());

        then(eventPublisher).should(never()).publishEvent(any(GuardianNotifyRequestedEvent.class));
    }

    @Test
    @DisplayName("recordStep: 해소된 에스컬레이션의 EMERGENCY_CALL → SKIPPED로 강제 기록")
    void recordStep_emergencyCall_resolvedEscalation_forcesSkipped() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);
        escalation.resolve(ResolutionType.GUARDIAN_HANDLED, null);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.EMERGENCY_CALL))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EscalationStepResponse res = escalationService.recordStep(10L, emergencyCallRequest());

        assertThat(res.status()).isEqualTo(StepStatus.SKIPPED);
        assertThat(res.escalationStatus()).isEqualTo(EscalationStatus.RESOLVED);
    }

    @Test
    @DisplayName("recordStep: 진행 중 에스컬레이션의 EMERGENCY_CALL → 요청 status 그대로 기록")
    void recordStep_emergencyCall_inProgress_keepsRequestedStatus() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.EMERGENCY_CALL))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        EscalationStepResponse res = escalationService.recordStep(10L, emergencyCallRequest());

        assertThat(res.status()).isEqualTo(StepStatus.EXECUTED);
        assertThat(res.escalationStatus()).isEqualTo(EscalationStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("recordStep: VOICE_CHECK RESPONDED + USER_OK → SELF_RESOLVED 자동 해소")
    void recordStep_voiceCheck_userOk_autoResolves() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = voiceCheckStep();
        ReflectionTestUtils.setField(existing, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.of(existing));

        EscalationStepResponse res = escalationService.recordStep(10L,
                voiceCheckRespondedRequest("USER_OK"));

        assertThat(escalation.getStatus()).isEqualTo(EscalationStatus.RESOLVED);
        assertThat(escalation.getResolutionType()).isEqualTo(ResolutionType.SELF_RESOLVED);
        assertThat(escalation.getResolvedAt()).isNotNull();
        assertThat(res.escalationStatus()).isEqualTo(EscalationStatus.RESOLVED);
    }

    @Test
    @DisplayName("recordStep: VOICE_CHECK RESPONDED + USER_NEEDS_HELP → 해소 없이 진행 유지")
    void recordStep_voiceCheck_needsHelp_staysInProgress() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = voiceCheckStep();
        ReflectionTestUtils.setField(existing, "id", 100L);

        given(escalationRepository.findByIdForUpdate(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.of(existing));

        EscalationStepResponse res = escalationService.recordStep(10L,
                voiceCheckRespondedRequest("USER_NEEDS_HELP"));

        assertThat(escalation.getStatus()).isEqualTo(EscalationStatus.IN_PROGRESS);
        assertThat(res.escalationStatus()).isEqualTo(EscalationStatus.IN_PROGRESS);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // listEscalations (E1)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("listEscalations: 관계 있음 → 에스컬레이션 목록 반환")
    void listEscalations_happyPath() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 20L);

        Page<Escalation> page = new PageImpl<>(List.of(escalation));
        given(escalationRepository.findByCareTargetId(eq(CARE_TARGET_ID), any(Pageable.class))).willReturn(page);

        PageResponse<EscalationSummaryResponse> result =
                escalationService.listEscalations(USER_ID, CARE_TARGET_ID, PageRequest.of(0, 20));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).escalationId()).isEqualTo(20L);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listEscalations: 관계 없음(노인 존재) → ACCESS_DENIED")
    void listEscalations_noRelationship_accessDenied() {
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requireRelationship(USER_ID, CARE_TARGET_ID);

        assertThatThrownBy(() -> escalationService.listEscalations(USER_ID, CARE_TARGET_ID, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("listEscalations: 관계 없음(노인 미존재) → CARE_TARGET_NOT_FOUND")
    void listEscalations_careTargetNotFound() {
        willThrow(new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND))
                .given(accessValidator).requireRelationship(USER_ID, CARE_TARGET_ID);

        assertThatThrownBy(() -> escalationService.listEscalations(USER_ID, CARE_TARGET_ID, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CareTargetErrorCode.CARE_TARGET_NOT_FOUND));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getDetail (E2)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getDetail: 정상 조회 → 에스컬레이션 상세 + steps(step_order 오름차순) 반환")
    void getDetail_happyPath() {
        Escalation escalation = makeEscalationWithCareTarget(30L, CARE_TARGET_ID);

        EscalationStep s1 = voiceCheckStep();
        ReflectionTestUtils.setField(s1, "id", 200L);
        EscalationStep s2 = guardianNotifyStep();
        ReflectionTestUtils.setField(s2, "id", 201L);

        given(escalationRepository.findById(30L)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));
        given(careTargetRepository.findById(CARE_TARGET_ID))
                .willReturn(Optional.of(CareTarget.create("박순자", null, Gender.FEMALE, null, null)));
        given(escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(30L))
                .willReturn(List.of(s1, s2));

        EscalationDetailResponse res = escalationService.getDetail(USER_ID, 30L);

        assertThat(res.escalationId()).isEqualTo(30L);
        assertThat(res.careTargetId()).isEqualTo(CARE_TARGET_ID);
        assertThat(res.careTargetName()).isEqualTo("박순자");
        assertThat(res.eventType()).isEqualTo(EventType.FALL);
        // 앱이 이 ID로 S3 포즈클립을 연다 — 빠지면 응급 상세에서 리플레이 진입이 불가능해진다
        assertThat(res.sensingEventId()).isEqualTo(5L);
        assertThat(res.steps()).hasSize(2);
        assertThat(res.steps().get(0).stepType()).isEqualTo(StepType.VOICE_CHECK);
        assertThat(res.steps().get(1).stepType()).isEqualTo(StepType.GUARDIAN_NOTIFY);
    }

    @Test
    @DisplayName("getDetail: 존재하지 않는 escalation_id → ESCALATION_NOT_FOUND")
    void getDetail_notFound() {
        given(escalationRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> escalationService.getDetail(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_NOT_FOUND));
    }

    @Test
    @DisplayName("getDetail: 관계 없음(노인 존재) → ACCESS_DENIED")
    void getDetail_accessDenied() {
        Escalation escalation = makeEscalationWithCareTarget(31L, CARE_TARGET_ID);

        given(escalationRepository.findById(31L)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED))
                .given(accessValidator).requireRelationship(USER_ID, CARE_TARGET_ID);

        assertThatThrownBy(() -> escalationService.getDetail(USER_ID, 31L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(CommonErrorCode.ACCESS_DENIED));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // resolve (E3)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("resolve: IN_PROGRESS → RESOLVED, memo·resolvedAt 반영")
    void resolve_happyPath() {
        Escalation escalation = makeEscalationWithCareTarget(40L, CARE_TARGET_ID);

        given(escalationRepository.findByIdForUpdate(40L)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));
        given(escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(40L)).willReturn(List.of());

        EscalationDetailResponse res = escalationService.resolve(USER_ID, 40L,
                new EscalationResolveRequest(ResolutionType.GUARDIAN_HANDLED, "직접 방문 확인"));

        assertThat(res.status()).isEqualTo(EscalationStatus.RESOLVED);
        assertThat(res.resolutionType()).isEqualTo(ResolutionType.GUARDIAN_HANDLED);
        assertThat(res.resolutionMemo()).isEqualTo("직접 방문 확인");
        assertThat(res.resolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("resolve: 이미 RESOLVED → ESCALATION_ALREADY_RESOLVED")
    void resolve_alreadyResolved() {
        Escalation escalation = makeEscalationWithCareTarget(41L, CARE_TARGET_ID);
        // 먼저 한 번 해제
        escalation.resolve(ResolutionType.FALSE_ALARM, null);

        given(escalationRepository.findByIdForUpdate(41L)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));

        assertThatThrownBy(() -> escalationService.resolve(USER_ID, 41L,
                new EscalationResolveRequest(ResolutionType.GUARDIAN_HANDLED, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_ALREADY_RESOLVED));
    }

    @Test
    @DisplayName("resolve: SELF_RESOLVED(허용 안 됨) → INVALID_RESOLUTION_TYPE")
    void resolve_invalidResolutionType() {
        Escalation escalation = makeEscalationWithCareTarget(42L, CARE_TARGET_ID);

        given(escalationRepository.findByIdForUpdate(42L)).willReturn(Optional.of(escalation));
        given(riskAssessmentRepository.findById(any())).willReturn(Optional.of(makeRiskAssessment()));
        given(sensingEventRepository.findById(any())).willReturn(Optional.of(makeEvent(CARE_TARGET_ID)));

        assertThatThrownBy(() -> escalationService.resolve(USER_ID, 42L,
                new EscalationResolveRequest(ResolutionType.SELF_RESOLVED, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.INVALID_RESOLUTION_TYPE));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private EscalationStep voiceCheckStep() {
        return EscalationStep.record(10L, StepType.VOICE_CHECK, (short) 1,
                StepStatus.EXECUTED, EXECUTED_AT, null, null);
    }

    private EscalationStep guardianNotifyStep() {
        return EscalationStep.record(10L, StepType.GUARDIAN_NOTIFY, (short) 2,
                StepStatus.EXECUTED, EXECUTED_AT, null, null);
    }

    private EscalationStepRequest voiceCheckRequest() {
        return voiceCheckRequest(StepStatus.EXECUTED);
    }

    private EscalationStepRequest voiceCheckRequest(StepStatus status) {
        return new EscalationStepRequest(
                StepType.VOICE_CHECK, 1, status, EXECUTED_AT,
                null, null, null
        );
    }

    private EscalationStepRequest voiceCheckRespondedRequest(String responseResult) {
        return new EscalationStepRequest(
                StepType.VOICE_CHECK, 1, StepStatus.RESPONDED, EXECUTED_AT,
                EXECUTED_AT.plusSeconds(8),
                Map.of("response_result", responseResult, "stt_provider", "whisper"),
                null
        );
    }

    private EscalationStepRequest emergencyCallRequest() {
        return new EscalationStepRequest(
                StepType.EMERGENCY_CALL, 3, StepStatus.EXECUTED, EXECUTED_AT,
                null, null, null
        );
    }

    private EscalationStepRequest guardianNotifyRequest() {
        return new EscalationStepRequest(
                StepType.GUARDIAN_NOTIFY, 2, StepStatus.EXECUTED, EXECUTED_AT,
                null, null,
                new EscalationStepRequest.GuardianMessage(
                        "낙상 의심 상황이 감지되었습니다.",
                        "14시 22분 침실에서 낙상 가능성이 높은 움직임이 감지되었습니다.",
                        "즉시 전화 또는 방문 확인을 권장합니다."
                )
        );
    }

    /** escalation(id) with riskAssessmentId=1 → chain mocking 편의 */
    private Escalation makeEscalationWithCareTarget(Long escalationId, Long careTargetId) {
        Escalation e = Escalation.start(1L);
        ReflectionTestUtils.setField(e, "id", escalationId);
        return e;
    }

    private RiskAssessment makeRiskAssessment() {
        RiskAssessment ra = RiskAssessment.of(1L, (short) 85, RiskLevel.DANGER, null, "v0.1", EXECUTED_AT);
        ReflectionTestUtils.setField(ra, "id", 1L);
        ReflectionTestUtils.setField(ra, "sensingEventId", 5L);
        return ra;
    }

    private SensingEvent makeEvent(Long careTargetId) {
        SensingEvent e = SensingEvent.create(careTargetId, null, EventType.FALL, null,
                null, null, null, null, "v0.1", null, EXECUTED_AT);
        ReflectionTestUtils.setField(e, "id", 5L);
        return e;
    }
}
