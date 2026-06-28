package com.notifi.server.domain.sensing.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareRelationshipRepository;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.repository.DeviceRepository;
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
import com.notifi.server.global.exception.CommonErrorCode;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
    private final CareRelationshipRepository careRelationshipRepository;
    private final CareTargetRepository careTargetRepository;

    // ── S1: 실시간 상태 대시보드 ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public CareTargetStatusResponse getStatus(Long userId, Long careTargetId) {
        verifyRelationship(userId, careTargetId);

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

        return new CareTargetStatusResponse(
                careTargetId,
                currentRiskLevel,
                lastActivityAt,
                null,    // todayMetrics: tb_activity_aggregate 미구현
                devices,
                null     // activeEscalation: E 도메인 보류
        );
    }

    // ── S2: 감지 이벤트 목록 ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<SensingEventSummaryResponse> getEvents(
            Long userId, Long careTargetId,
            EventType eventType, Instant from, Instant to,
            Pageable pageable) {

        verifyRelationship(userId, careTargetId);

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
        verifyRelationship(userId, event.getCareTargetId());
        PoseClip clip = poseClipRepository.findBySensingEventId(sensingEventId)
                .orElseThrow(() -> new BusinessException(SensingErrorCode.POSE_CLIP_NOT_FOUND));
        return PoseClipResponse.from(clip);
    }

    // ── private ───────────────────────────────────────────────────────────────
    private void verifyRelationship(Long userId, Long careTargetId) {
        if (!careRelationshipRepository.existsByUserIdAndCareTargetId(userId, careTargetId)) {
            if (careTargetRepository.existsById(careTargetId)) {
                throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
            }
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
        }
    }
}
