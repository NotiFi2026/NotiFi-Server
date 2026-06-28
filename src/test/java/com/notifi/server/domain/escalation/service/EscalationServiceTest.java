package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.escalation.dto.EscalationStepRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepResponse;
import com.notifi.server.domain.escalation.entity.*;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.notification.service.NotificationService;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.entity.EventType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EscalationServiceTest {

    @Mock EscalationRepository escalationRepository;
    @Mock EscalationStepRepository escalationStepRepository;
    @Mock RiskAssessmentRepository riskAssessmentRepository;
    @Mock SensingEventRepository sensingEventRepository;
    @Mock NotificationService notificationService;

    @InjectMocks EscalationService escalationService;

    private static final Instant EXECUTED_AT = Instant.parse("2026-06-28T03:22:00Z");

    // ── VOICE_CHECK 단계 신규 기록 ───────────────────────────────────────────

    @Test
    @DisplayName("recordStep: VOICE_CHECK 신규 → step 저장, 알림 미발송")
    void recordStep_voiceCheck_new_noNotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep step = voiceCheckStep();
        ReflectionTestUtils.setField(step, "id", 100L);

        given(escalationRepository.findById(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);

        EscalationStepResponse res = escalationService.recordStep(10L, voiceCheckRequest());

        assertThat(res.stepId()).isEqualTo(100L);
        assertThat(res.stepType()).isEqualTo(StepType.VOICE_CHECK);
        then(notificationService).should(never()).dispatchGuardianNotify(any(), any(), any());
    }

    // ── GUARDIAN_NOTIFY 신규 → FCM 트리거 ────────────────────────────────────

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

        SensingEvent event = SensingEvent.create(99L, null, EventType.FALL,
                null, null, null, null, "v0.1", null, EXECUTED_AT);
        ReflectionTestUtils.setField(event, "id", 5L);

        given(escalationRepository.findById(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.GUARDIAN_NOTIFY))
                .willReturn(Optional.empty());
        given(escalationStepRepository.save(any())).willReturn(step);
        given(riskAssessmentRepository.findById(1L)).willReturn(Optional.of(ra));
        given(sensingEventRepository.findById(5L)).willReturn(Optional.of(event));

        escalationService.recordStep(10L, guardianNotifyRequest());

        then(notificationService).should().dispatchGuardianNotify(
                eq(101L), eq(99L), any(EscalationStepRequest.GuardianMessage.class));
    }

    // ── 멱등: 동일 step_type 재요청 → updateProgress, FCM 미발송 ─────────────

    @Test
    @DisplayName("recordStep: 동일 step_type 재요청 → updateProgress 호출, 알림 미발송, save 미호출")
    void recordStep_duplicate_updatesProgress_noNotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = voiceCheckStep();
        ReflectionTestUtils.setField(existing, "id", 100L);

        given(escalationRepository.findById(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.VOICE_CHECK))
                .willReturn(Optional.of(existing));

        escalationService.recordStep(10L, voiceCheckRequest());

        then(escalationStepRepository).should(never()).save(any());
        then(notificationService).should(never()).dispatchGuardianNotify(any(), any(), any());
        assertThat(existing.getStatus()).isEqualTo(StepStatus.EXECUTED);
    }

    // ── 존재하지 않는 escalation_id → 예외 ──────────────────────────────────

    @Test
    @DisplayName("recordStep: 존재하지 않는 escalation_id → ESCALATION_NOT_FOUND")
    void recordStep_unknownEscalation_throws() {
        given(escalationRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> escalationService.recordStep(99L, voiceCheckRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(EscalationErrorCode.ESCALATION_NOT_FOUND));

        then(escalationStepRepository).should(never()).save(any());
    }

    // ── GUARDIAN_NOTIFY 재요청 → FCM 미발송 (중복 방지) ──────────────────────

    @Test
    @DisplayName("recordStep: GUARDIAN_NOTIFY 재요청 → FCM 재발송 없음")
    void recordStep_guardianNotify_duplicate_noRenotification() {
        Escalation escalation = Escalation.start(1L);
        ReflectionTestUtils.setField(escalation, "id", 10L);

        EscalationStep existing = guardianNotifyStep();
        ReflectionTestUtils.setField(existing, "id", 101L);

        given(escalationRepository.findById(10L)).willReturn(Optional.of(escalation));
        given(escalationStepRepository.findByEscalationIdAndStepType(10L, StepType.GUARDIAN_NOTIFY))
                .willReturn(Optional.of(existing));

        escalationService.recordStep(10L, guardianNotifyRequest());

        then(notificationService).should(never()).dispatchGuardianNotify(any(), any(), any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private EscalationStep voiceCheckStep() {
        return EscalationStep.record(10L, StepType.VOICE_CHECK, (short) 1,
                StepStatus.EXECUTED, EXECUTED_AT, null, null);
    }

    private EscalationStep guardianNotifyStep() {
        return EscalationStep.record(10L, StepType.GUARDIAN_NOTIFY, (short) 2,
                StepStatus.EXECUTED, EXECUTED_AT, null, null);
    }

    private EscalationStepRequest voiceCheckRequest() {
        return new EscalationStepRequest(
                StepType.VOICE_CHECK, 1, StepStatus.EXECUTED, EXECUTED_AT,
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
}
