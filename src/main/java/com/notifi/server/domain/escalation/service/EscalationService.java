package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.caretarget.entity.CareTarget;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
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
import com.notifi.server.domain.escalation.event.GuardianNotifyRequestedEvent;
import com.notifi.server.domain.escalation.event.VoiceCheckRequestedEvent;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
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
    private final CareTargetRepository careTargetRepository;
    private final CareTargetAccessValidator accessValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public EscalationStepResponse recordStep(Long escalationId, EscalationStepRequest req) {
        // 행 잠금 — E3 resolve와 직렬화해 SKIPPED 판정·USER_OK 자동 해소가 최신 상태 기준이 되게 함
        Escalation escalation = escalationRepository.findByIdForUpdate(escalationId)
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

        // 노인이 음성 확인에 "괜찮다"(USER_OK)고 응답하면 에스컬레이션 자동 해소
        if (req.stepType() == StepType.VOICE_CHECK
                && req.status() == StepStatus.RESPONDED
                && escalation.getStatus() == EscalationStatus.IN_PROGRESS
                && req.responseDetail() != null
                && "USER_OK".equals(req.responseDetail().get("response_result"))) {
            escalation.resolve(ResolutionType.SELF_RESOLVED, "음성 확인 USER_OK 응답 자동 해소");
        }

        // 신규 GUARDIAN_NOTIFY 단계일 때만 FCM 발송 (재시도 시 중복 발송 방지)
        // 커밋 이후(AFTER_COMMIT 리스너) 실행 — 유령 푸시 방지 + 행 잠금 밖에서 네트워크 호출
        if (isNew && req.stepType() == StepType.GUARDIAN_NOTIFY && req.guardianMessage() != null) {
            Long careTargetId = resolveCareTargetId(escalation);
            eventPublisher.publishEvent(new GuardianNotifyRequestedEvent(
                    step.getId(), escalationId, careTargetId, req.guardianMessage()));
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
        // 노인 본인도 자기 응급 이력을 본다
        accessValidator.requireRelationshipOrSelf(userId, careTargetId);
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
        SensingEvent event = resolveSensingEvent(escalation);
        accessValidator.requireRelationshipOrSelf(userId, event.getCareTargetId());
        List<EscalationStep> steps =
                escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(escalationId);
        return buildDetail(escalation, steps, event);
    }

    // ── E3: 보호자 확인·해제 ──────────────────────────────────────────────────
    @Transactional
    public EscalationDetailResponse resolve(Long userId, Long escalationId, EscalationResolveRequest req) {
        // 행 잠금 — recordStep(I2)과 직렬화 (동시 중복 resolve도 뒤진 쪽이 409)
        Escalation escalation = escalationRepository.findByIdForUpdate(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        SensingEvent event = resolveSensingEvent(escalation);
        accessValidator.requireRelationship(userId, event.getCareTargetId());

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
        return buildDetail(escalation, steps, event);
    }

    // ── E4: 노인 본인 "괜찮아요" ──────────────────────────────────────────────
    /**
     * 노인이 앱에서 직접 안전을 알린다 — 음성 확인이 안 될 때의 대안 경로.
     *
     * <p>E3(보호자 해제)를 열어 주지 않는 이유: {@code GUARDIAN_HANDLED}·{@code FALSE_ALARM}은
     * <b>보호자가 상황을 판단했다는 의미</b>다. 노인이 "괜찮다"고 하는 것은 그것과 다르고,
     * 시스템에 이미 대응하는 개념이 있다 — 음성 확인이 USER_OK를 받았을 때의 {@code SELF_RESOLVED}.
     * 같은 결과로 모아 두면 "노인이 스스로 괜찮다고 했다"가 어느 경로로 들어오든 한 가지로 읽힌다.
     *
     * <p>보호자는 이 경로를 쓸 수 없다 — 남이 대신 눌러 주면 자기응답의 의미가 사라진다.
     */
    @Transactional
    public EscalationDetailResponse selfConfirmSafe(Long userId, Long escalationId) {
        // 행 잠금 — recordStep(I2)·resolve(E3)와 직렬화. 음성 응답과 버튼이 동시에 들어와도 한쪽만 이긴다
        Escalation escalation = escalationRepository.findByIdForUpdate(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        SensingEvent event = resolveSensingEvent(escalation);
        accessValidator.requireSelf(userId, event.getCareTargetId());

        if (escalation.getStatus() != EscalationStatus.IN_PROGRESS) {
            throw new BusinessException(EscalationErrorCode.ESCALATION_ALREADY_RESOLVED);
        }
        escalation.resolve(ResolutionType.SELF_RESOLVED, "노인 본인 앱 응답 자동 해소");

        List<EscalationStep> steps =
                escalationStepRepository.findByEscalationIdOrderByStepOrderAsc(escalationId);
        return buildDetail(escalation, steps, event);
    }

    // ── private ───────────────────────────────────────────────────────────────
    private Long resolveCareTargetId(Escalation escalation) {
        return resolveSensingEvent(escalation).getCareTargetId();
    }

    private SensingEvent resolveSensingEvent(Escalation escalation) {
        RiskAssessment ra = riskAssessmentRepository.findById(escalation.getRiskAssessmentId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        return sensingEventRepository.findById(ra.getSensingEventId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
    }

    private EscalationDetailResponse buildDetail(Escalation escalation, List<EscalationStep> steps,
                                                 SensingEvent event) {
        String careTargetName = careTargetRepository.findById(event.getCareTargetId())
                .map(CareTarget::getName)
                .orElse(null);
        return EscalationDetailResponse.of(
                escalation, steps, event.getCareTargetId(), careTargetName,
                event.getEventType(), event.getId());
    }
}
