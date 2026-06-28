package com.notifi.server.domain.escalation.service;

import com.notifi.server.domain.escalation.dto.EscalationStepRequest;
import com.notifi.server.domain.escalation.dto.EscalationStepResponse;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepType;
import com.notifi.server.domain.escalation.exception.EscalationErrorCode;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.notification.service.NotificationService;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRepository escalationRepository;
    private final EscalationStepRepository escalationStepRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final SensingEventRepository sensingEventRepository;
    private final NotificationService notificationService;

    @Transactional
    public EscalationStepResponse recordStep(Long escalationId, EscalationStepRequest req) {
        Escalation escalation = escalationRepository.findById(escalationId)
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));

        Optional<EscalationStep> existing =
                escalationStepRepository.findByEscalationIdAndStepType(escalationId, req.stepType());

        EscalationStep step;
        boolean isNew;

        if (existing.isPresent()) {
            step = existing.get();
            step.updateProgress(req.status(), req.executedAt(), req.respondedAt(), req.responseDetail());
            isNew = false;
        } else {
            step = escalationStepRepository.save(EscalationStep.record(
                    escalationId, req.stepType(), req.stepOrder().shortValue(),
                    req.status(), req.executedAt(), req.respondedAt(), req.responseDetail()
            ));
            isNew = true;
        }

        // 신규 GUARDIAN_NOTIFY 단계일 때만 FCM 발송 (재시도 시 중복 발송 방지)
        if (isNew && req.stepType() == StepType.GUARDIAN_NOTIFY && req.guardianMessage() != null) {
            Long careTargetId = resolveCareTargetId(escalation);
            notificationService.dispatchGuardianNotify(step.getId(), careTargetId, req.guardianMessage());
        }

        return EscalationStepResponse.from(step);
    }

    private Long resolveCareTargetId(Escalation escalation) {
        RiskAssessment ra = riskAssessmentRepository.findById(escalation.getRiskAssessmentId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        SensingEvent event = sensingEventRepository.findById(ra.getSensingEventId())
                .orElseThrow(() -> new BusinessException(EscalationErrorCode.ESCALATION_NOT_FOUND));
        return event.getCareTargetId();
    }
}
