package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.escalation.dto.EscalationDetailResponse;
import com.notifi.server.domain.escalation.dto.EscalationResolveRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepResponse;
import com.notifi.server.domain.escalation.dto.EscalationSummaryResponse;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.ResolutionType;
import com.notifi.server.domain.escalation.entity.StepStatus;
import com.notifi.server.domain.escalation.entity.StepType;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.notification.service.NotificationService;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRepository escalationRepository;
    private final EscalationStepRepository escalationStepRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final SensingEventRepository sensingEventRepository;
    private final NotificationService notificationService;
    private final CareTargetAccessValidator accessValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EscalationStepResponse recordStep(Long escalationId, EscalationStepRequest req) {
        Escalation escalation = escalationRepository.findById(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));

        Optional<EscalationStep> existing =
                escalationStepRepository.findByEscalationIdAndStepType(escalationId, req.stepType());

        // 이미 해소된 에스컬레이션의 EMERGENCY_CALL은 SKIPPED로 강제 — 보호자 확인 시 119 진행 차단
        StepStatus effectiveStatus = req.status();
        if (req.stepType() == StepType.EMERGENCY_CALL
                && escalation.getStatus() != EscalationStatus.IN_PROGRESS) {
            effectiveStatus = StepStatus.SKIPPED;
        }

        EscalationStep step;
        boolean isNew;

        if (existing.isPresent()) {
            step = existing.get();
            step.updateProgress(effectiveStatus, req.executedAt(), req.respondedAt(), req.responseDetail());
            isNew = false;
        } else {
            step = escalationStepRepository.save(EscalationStep.record(
                    escalationId, req.stepType(), req.stepOrder().shortValue(),
                    effectiveStatus, req.executedAt(), req.respondedAt(), req.responseDetail()
            ));
            isNew = true;
        }

        // 신규 GUARDIAN_NOTIFY 단계일 때만 FCM 발송 (재시도 시 중복 발송 방지)
        if (isNew && req.stepType() == StepType.GUARDIAN_NOTIFY && req.guardianMessage() != null) {
            Long careTargetId = resolveCareTargetId(escalation);
            notificationService.dispatchGuardianNotify(step.getId(), careTargetId, req.guardianMessage());
        }

        // 신규 VOICE_CHECK 진행 단계면 노인 앱으로 음성확인 푸시 (사후 기록 SKIPPED 등은 제외)
        // FCM 발송은 커밋 이후(AFTER_COMMIT 리스너)에 실행 — 롤백 시 유령 푸시 방지
        if (isNew && req.stepType() == StepType.VOICE_CHECK
                && (req.status() == StepStatus.PENDING || req.status() == StepStatus.EXECUTED)) {
            Long careTargetId = resolveCareTargetId(escalation);
            eventPublisher.publishEvent(
                    new VoiceCheckRequestedEvent(step.getId(), escalationId, careTargetId));
        }

        return EscalationStepResponse.from(step, escalation.getStatus());
    }

    // ── E1: 에스컬레이션 목록 ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<EscalationSummaryResponse> listEscalations(
            Long userId, Long careTargetId, Pageable pageable) {
        accessValidator.requireRelationship(userId, careTargetId);
        Page<EscalationSummaryResponse> page =
                escalationRepository.findByCareTargetId(careTargetId, pageable)
                        .map(EscalationSummaryResponse::from);
        return PageResponse.from(page);
    }

    // ── E2: 에스컬레이션 상세 ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public EscalationDetailResponse getDetail(Long userId, Long escalationId) {
        Escalation escalation = escalationRepository.findById(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        Long careTargetId = resolveCareTargetId(escalation);
        accessValidator.requireRelationship(userId, careTargetId);
        List<EscalationStep> steps =
                escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(escalationId);
        return EscalationDetailResponse.of(escalation, steps);
    }

    // ── E3: 보호자 확인·해제 ──────────────────────────────────────────────────
    @Transactional
    public EscalationDetailResponse resolve(Long userId, Long escalationId, EscalationResolveRequest req) {
        Escalation escalation = escalationRepository.findById(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        Long careTargetId = resolveCareTargetId(escalation);
        accessValidator.requireRelationship(userId, careTargetId);

        if (escalation.getStatus() != EscalationStatus.IN_PROGRESS) {
            throw new BusinessException(EscalationErrorCode.ESCALATION_ALREADY_RESOLVED);
        }
        if (req.resolutionType() != ResolutionType.GUARDIAN_HANDLED
                && req.resolutionType() != ResolutionType.FALSE_ALARM) {
            throw new BusinessException(EscalationErrorCode.INVALID_RESOLUTION_TYPE);
        }

        escalation.resolve(req.resolutionType(), req.memo());

        List<EscalationStep> steps =
                escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(escalationId);
        return EscalationDetailResponse.of(escalation, steps);
    }

    // ── private ───────────────────────────────────────────────────────────────
    private Long resolveCareTargetId(Escalation escalation) {
        RiskAssessment ra = riskAssessmentRepository.findById(escalation.getRiskAssessmentId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        SensingEvent event = sensingEventRepository.findById(ra.getSensingEventId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        return event.getCareTargetId();
    }
}
