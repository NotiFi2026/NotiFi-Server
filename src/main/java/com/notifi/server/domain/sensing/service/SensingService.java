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
import com.notifi.server.domain.sensing.entity.PoseClip;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import com.notifi.server.domain.sensing.exception.SensingErrorCode;
import com.notifi.server.domain.sensing.repository.PoseClipRepository;
import com.notifi.server.domain.sensing.repository.RiskAssessmentRepository;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensingService {

    /** 같은 낙상으로 보고 대응을 재사용하는 기간. {@link #findOngoingEscalation} 참고. */
    private static final Duration ONGOING_WINDOW = Duration.ofMinutes(5);

    private final SensingEventRepository sensingEventRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final EscalationRepository escalationRepository;
    private final CareTargetRepository careTargetRepository;
    private final PoseClipRepository poseClipRepository;

    @Transactional
    public SensingEventIngestResponse ingest(SensingEventIngestRequest req) {
        if (!careTargetRepository.existsById(req.careTargetId())) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
        }

        // 계약: detected_at은 ms 정밀도(추론 윈도 종료시각) — 멱등키 비교·저장 정밀도를 ms로 통일
        Instant detectedAt = req.detectedAt().truncatedTo(ChronoUnit.MILLIS);

        Optional<SensingEvent> existing = sensingEventRepository
                .findByCareTargetIdAndDetectedAtAndEventType(
                        req.careTargetId(), detectedAt, req.eventType());

        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getActivityClass(), req.activityClass())) {
                // 재시도는 동일 payload가 계약 — 불일치는 AI측 버그 신호이므로 기록만 남긴다
                log.warn("[I1] 멱등 재시도 activity_class 불일치: sensingEventId={}, 기존={}, 요청={}",
                        existing.get().getId(), existing.get().getActivityClass(), req.activityClass());
            }
            return buildIdempotentResponse(existing.get());
        }

        if (req.activityClass() != null && req.activityClass().expectedEventType() != req.eventType()) {
            // 조합 검증으로 거부하지 않는다 — 선택 필드가 응급 이벤트 적재를 막으면 안 됨. AI측 버그 신호로 관측만.
            log.warn("[I1] event_type·activity_class 불일치: eventType={}, activityClass={} (기대 event_type={})",
                    req.eventType(), req.activityClass(), req.activityClass().expectedEventType());
        }

        SensingEvent event;
        try {
            event = sensingEventRepository.save(SensingEvent.create(
                    req.careTargetId(), req.deviceId(), req.eventType(), req.activityClass(),
                    req.riskProbability(), req.anomalyScore(), req.trendScore(),
                    req.sensorStatus(), req.modelVersion(), req.features(), detectedAt
            ));
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 인제스트 경합만 409 — AI 서버 재시도 시 위 멱등 경로가 기존 결과를 응답.
            // FK·CHECK 등 다른 제약 위반을 중복으로 오보고하지 않도록 제약명으로 한정한다.
            if (isConstraintViolation(e, "uq_sensing_event_identity")) {
                throw new BusinessException(SensingErrorCode.SENSING_EVENT_ALREADY_EXISTS);
            }
            throw e;
        }

        RiskAssessment ra = riskAssessmentRepository.save(RiskAssessment.of(
                event.getId(), req.riskScore(), req.riskLevel(),
                req.scoreBreakdown(), req.modelVersion(), detectedAt
        ));

        if (shouldEscalate(req.riskLevel())) {
            Escalation ongoing = findOngoingEscalation(req.careTargetId());
            if (ongoing != null) {
                // 이미 대응이 돌고 있다 — 겹쳐 시작하지 않는다.
                //
                // 낙상 한 번에 겹치는 윈도가 여러 개 나오고, 그때마다 새 에스컬레이션을 만들면
                // 음성 확인이 처음부터 다시 시작되고("괜찮다고 했는데 또 물어본다") 119 단계도
                // 중복으로 걸린다. AI 서버에 쿨다운이 있지만 그건 그쪽 사정이고,
                // 여기가 최종 방어선이어야 한다.
                //
                // escalation_triggered=false로 답하는 것이 핵심이다 — AI 에이전트는 이 값으로
                // 새 대응 흐름을 시작할지 판단한다. id는 함께 주어 호출자가 진행 중인 건을
                // 가리킬 수 있게 한다.
                return new SensingEventIngestResponse(event.getId(), ra.getId(), false, ongoing.getId());
            }
            Escalation escalation = escalationRepository.save(Escalation.start(ra.getId()));
            return new SensingEventIngestResponse(event.getId(), ra.getId(), true, escalation.getId());
        }

        return new SensingEventIngestResponse(event.getId(), ra.getId(), false, null);
    }

    /**
     * 지금 대응이 돌고 있는 에스컬레이션. 없으면 null.
     *
     * <p><b>기한을 두는 이유</b>: 119 단계까지 간 에스컬레이션은 사람이 앱에서 닫을 때까지
     * IN_PROGRESS로 남는다(에이전트가 스스로 종결하지 않는다 — 구급대 도착 여부를 서버가 알 수
     * 없기 때문). 그걸 기한 없이 "대응 중"으로 보면 몇 시간 뒤의 진짜 낙상이 새 에스컬레이션도
     * 못 만들고 조용히 묻힌다. 겹치는 윈도를 막으려다 응급을 막는 셈이다.
     *
     * <p>창은 한 번의 낙상이 만드는 겹침(윈도 10초·stride 2초)과 대응 흐름(음성확인 → 60초 대기
     * → 119)을 덮을 만큼이면 충분하다.
     */
    private Escalation findOngoingEscalation(Long careTargetId) {
        return escalationRepository
                .findOngoingSince(
                        careTargetId, EscalationStatus.IN_PROGRESS,
                        Instant.now().minus(ONGOING_WINDOW), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public PoseClipIngestResponse ingestPoseClip(Long sensingEventId, PoseClipIngestRequest req) {
        if (!sensingEventRepository.existsById(sensingEventId)) {
            throw new BusinessException(SensingErrorCode.SENSING_EVENT_NOT_FOUND);
        }

        Optional<PoseClip> existing = poseClipRepository.findBySensingEventId(sensingEventId);
        if (existing.isPresent()) {
            return new PoseClipIngestResponse(existing.get().getId(), sensingEventId);
        }

        PoseClip clip;
        try {
            clip = poseClipRepository.save(PoseClip.of(
                    sensingEventId, req.modelVersion(), req.jointSchema(),
                    req.fps().shortValue(), req.frameCount(), req.durationMs(),
                    req.windowStartAt(), req.windowEndAt(),
                    req.frames(), req.eventTimeline()
            ));
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 적재 경합만 409 — 다른 제약 위반은 그대로 전파
            if (isConstraintViolation(e, "uq_pose_clip_event")) {
                throw new BusinessException(SensingErrorCode.POSE_CLIP_ALREADY_EXISTS);
            }
            throw e;
        }
        return new PoseClipIngestResponse(clip.getId(), sensingEventId);
    }

    private boolean shouldEscalate(RiskLevel level) {
        return level == RiskLevel.DANGER;
    }

    private static boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        return e.getCause() instanceof ConstraintViolationException cve
                && constraintName.equalsIgnoreCase(cve.getConstraintName());
    }

    private SensingEventIngestResponse buildIdempotentResponse(SensingEvent event) {
        RiskAssessment ra = riskAssessmentRepository.findBySensingEventId(event.getId())
                .orElse(null);
        if (ra == null) {
            return new SensingEventIngestResponse(event.getId(), null, false, null);
        }

        Optional<Escalation> own = escalationRepository.findByRiskAssessmentId(ra.getId());
        if (own.isPresent()) {
            return new SensingEventIngestResponse(event.getId(), ra.getId(), true, own.get().getId());
        }

        // 이 이벤트가 만든 에스컬레이션은 없다. 두 경우가 섞여 있으므로 위험도로 가른다.
        //
        // DANGER인데 자기 것이 없다면 **진행 중인 건에 합쳐졌던 이벤트**다. 그때도 대응 id를
        // 돌려줘야 한다 — AI는 이 값으로 재신고 억제를 걸기 때문에, null을 주면 재시도할 때마다
        // 억제가 풀려 stride(2초)마다 I1이 다시 쏟아진다.
        //
        // DANGER가 아니면 애초에 에스컬레이션이 없는 게 정상이다. 여기서 진행 중 건을 주면
        // 무관한 이벤트가 남의 대응을 가리킨다.
        if (shouldEscalate(ra.getRiskLevel())) {
            Escalation ongoing = findOngoingEscalation(event.getCareTargetId());
            if (ongoing != null) {
                return new SensingEventIngestResponse(
                        event.getId(), ra.getId(), false, ongoing.getId());
            }
        }
        return new SensingEventIngestResponse(event.getId(), ra.getId(), false, null);
    }
}
