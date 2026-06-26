package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.sensing.dto.SensingEventIngestRequest;
import com.notifi.server.domain.sensing.dto.SensingEventIngestResponse;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
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
public class SensingService {

    private final SensingEventRepository sensingEventRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final EscalationRepository escalationRepository;
    private final CareTargetRepository careTargetRepository;

    @Transactional
    public SensingEventIngestResponse ingest(SensingEventIngestRequest req) {
        if (!careTargetRepository.existsById(req.careTargetId())) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
        }

        Optional<SensingEvent> existing = sensingEventRepository
                .findByCareTargetIdAndDetectedAtAndEventType(
                        req.careTargetId(), req.detectedAt(), req.eventType());

        if (existing.isPresent()) {
            return buildIdempotentResponse(existing.get());
        }

        SensingEvent event = sensingEventRepository.save(SensingEvent.create(
                req.careTargetId(), req.deviceId(), req.eventType(),
                req.riskProbability(), req.anomalyScore(), req.trendScore(),
                req.sensorStatus(), req.modelVersion(), req.features(), req.detectedAt()
        ));

        RiskAssessment ra = riskAssessmentRepository.save(RiskAssessment.of(
                event.getId(), req.riskScore(), req.riskLevel(),
                req.scoreBreakdown(), req.modelVersion(), req.detectedAt()
        ));

        if (shouldEscalate(req.riskLevel())) {
            Escalation escalation = escalationRepository.save(Escalation.start(ra.getId()));
            return new SensingEventIngestResponse(event.getId(), ra.getId(), true, escalation.getId());
        }

        return new SensingEventIngestResponse(event.getId(), ra.getId(), false, null);
    }

    private boolean shouldEscalate(RiskLevel level) {
        return level == RiskLevel.DANGER;
    }

    private SensingEventIngestResponse buildIdempotentResponse(SensingEvent event) {
        RiskAssessment ra = riskAssessmentRepository.findBySensingEventId(event.getId())
                .orElse(null);
        if (ra == null) {
            return new SensingEventIngestResponse(event.getId(), null, false, null);
        }
        Optional<Escalation> escalation = escalationRepository.findByRiskAssessmentId(ra.getId());
        return new SensingEventIngestResponse(
                event.getId(), ra.getId(),
                escalation.isPresent(), escalation.map(e -> e.getId()).orElse(null));
    }
}
