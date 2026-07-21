package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.repository.DeviceRepository;
import com.notifi.server.domain.escalation.dto.ActiveEscalationSummary;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.escalation.repository.EscalationRepository;
import com.notifi.server.domain.escalation.repository.EscalationStepRepository;
import com.notifi.server.domain.sensing.dto.CareTargetStatusResponse;
import com.notifi.server.domain.sensing.dto.DeviceStatusItem;
import com.notifi.server.domain.sensing.dto.PoseClipResponse;
import com.notifi.server.domain.sensing.dto.SensingEventSummaryResponse;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.PoseClip;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.exception.SensingErrorCode;
import com.notifi.server.domain.sensing.repository.PoseClipRepository;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensingQueryService {

    private final SensingEventRepository sensingEventRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final PoseClipRepository poseClipRepository;
    private final DeviceRepository deviceRepository;
    private final EscalationRepository escalationRepository;
    private final EscalationStepRepository escalationStepRepository;
    private final CareTargetAccessValidator accessValidator;

    // ── S1: 실시간 상태 대시보드 ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public CareTargetStatusResponse getStatus(Long userId, Long careTargetId) {
        // 보호자뿐 아니라 노인 본인 앱에서도 자기 상태를 볼 수 있다
        accessValidator.requireRelationshipOrSelf(userId, careTargetId);

        SensingEvent latest = sensingEventRepository
                .findFirstByCareTargetIdOrderByDetectedAtDesc(careTargetId)
                .orElse(null);

        RiskLevel currentRiskLevel = null;
        Instant lastActivityAt = null;
        if (latest != null) {
            lastActivityAt = latest.getDetectedAt();
            currentRiskLevel = riskAssessmentRepository
                    .findBySensingEventId(latest.getId())
                    .map(RiskAssessment::getRiskLevel)
                    .orElse(null);
        }

        List<DeviceStatusItem> devices = deviceRepository
                .findByCareTargetIdOrderByRegisteredAtAsc(careTargetId)
                .stream()
                .map(DeviceStatusItem::from)
                .toList();

        ActiveEscalationSummary activeEscalation = escalationRepository
                .findByCareTargetIdAndStatus(careTargetId, EscalationStatus.IN_PROGRESS, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(e -> ActiveEscalationSummary.of(e,
                        escalationStepRepository
                                .findFirstByEscalationIdOrderByStepOrderDesc(e.getId())
                                .orElse(null)))
                .orElse(null);

        return new CareTargetStatusResponse(
                careTargetId,
                currentRiskLevel,
                lastActivityAt,
                null,    // todayMetrics: tb_activity_aggregate 미구현
                devices,
                activeEscalation
        );
    }

    // ── S2: 감지 이벤트 목록 ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<SensingEventSummaryResponse> getEvents(
            Long userId, Long careTargetId,
            EventType eventType, Instant from, Instant to,
            Pageable pageable) {

        accessValidator.requireRelationship(userId, careTargetId);

        Page<SensingEvent> page = sensingEventRepository
                .findEvents(careTargetId, eventType, from, to, pageable);

        // 위험도·포즈클립 일괄 로드 (N+1 방지)
        List<Long> eventIds = page.getContent().stream()
                .map(SensingEvent::getId)
                .toList();
        Map<Long, RiskAssessment> raMap = riskAssessmentRepository
                .findBySensingEventIdIn(eventIds)
                .stream()
                .collect(Collectors.toMap(RiskAssessment::getSensingEventId, ra -> ra));
        Set<Long> clipEventIds = eventIds.isEmpty()
                ? Set.of()
                : new HashSet<>(poseClipRepository.findExistingSensingEventIds(eventIds));

        Page<SensingEventSummaryResponse> mapped = page.map(e ->
                SensingEventSummaryResponse.of(e, raMap.get(e.getId()), clipEventIds.contains(e.getId())));
        return PageResponse.from(mapped);
    }

    // ── S3: 복원 스켈레톤 리플레이 조회 ──────────────────────────────────────────
    @Transactional(readOnly = true)
    public PoseClipResponse getPoseClip(Long userId, Long sensingEventId) {
        SensingEvent event = sensingEventRepository.findById(sensingEventId)
                .orElseThrow(() -> new BusinessException(SensingErrorCode.SENSING_EVENT_NOT_FOUND));
        accessValidator.requireRelationship(userId, event.getCareTargetId());
        PoseClip clip = poseClipRepository.findBySensingEventId(sensingEventId)
                .orElseThrow(() -> new BusinessException(SensingErrorCode.POSE_CLIP_NOT_FOUND));
        return PoseClipResponse.from(clip);
    }
}
