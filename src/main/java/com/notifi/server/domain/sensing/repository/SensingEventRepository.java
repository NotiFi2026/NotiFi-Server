package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.ActivityClass;
import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SensingEventRepository extends JpaRepository<SensingEvent, Long> {

    Optional<SensingEvent> findByCareTargetIdAndDetectedAtAndEventType(
            Long careTargetId, Instant detectedAt, EventType eventType);

    // S1: 가장 최근 이벤트 1건 (위험도·last_activity_at 산출)
    // 같은 detected_at 동률(다른 event_type) 시 id 보조 정렬로 C2(findLatestPerCareTarget)와 동일 행 선택 보장
    Optional<SensingEvent> findFirstByCareTargetIdOrderByDetectedAtDescIdDesc(Long careTargetId);

    // C2: 노인별 최신 이벤트 일괄 조회 (N+1 방지, Postgres DISTINCT ON)
    @Query(value = "SELECT DISTINCT ON (care_target_id) * FROM tb_sensing_event " +
                   "WHERE care_target_id IN (:careTargetIds) " +
                   "ORDER BY care_target_id, detected_at DESC, sensing_event_id DESC",
           nativeQuery = true)
    List<SensingEvent> findLatestPerCareTarget(@Param("careTargetIds") Collection<Long> careTargetIds);

    // S2: 필터 페이지 조회 (nullable 파라미터)
    // `:param IS NULL OR` 패턴은 Postgres에서 "could not determine data type of parameter"로 실패한다
    // — COALESCE로 파라미터 타입을 컬럼에서 추론시킨다 (event_type·detected_at 모두 NOT NULL이라 의미 동일)
    @Query("SELECT se FROM SensingEvent se WHERE se.careTargetId = :ctId " +
           "AND se.eventType = COALESCE(:eventType, se.eventType) " +
           "AND se.detectedAt >= COALESCE(:from, se.detectedAt) " +
           "AND se.detectedAt <= COALESCE(:to, se.detectedAt)")
    Page<SensingEvent> findEvents(@Param("ctId") Long careTargetId,
                                  @Param("eventType") EventType eventType,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    // I6: 하루치 위험도별 건수. AI가 산출해 저장한 risk_level이 단일 출처다(Backend 재계산 없음).
    // 구간은 [from, to) 반개구간 — 자정 경계 이벤트가 양쪽 날짜에 이중 계상되지 않게 한다.
    @Query("SELECT ra.riskLevel AS riskLevel, COUNT(ra) AS total " +
           "FROM RiskAssessment ra, SensingEvent se " +
           "WHERE ra.sensingEventId = se.id AND se.careTargetId = :ctId " +
           "AND se.detectedAt >= :from AND se.detectedAt < :to " +
           "GROUP BY ra.riskLevel")
    List<RiskLevelCount> countByRiskLevel(@Param("ctId") Long careTargetId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    // I6: 하루치 activity_class별 건수. NULL(AI가 세부 분류를 안 보낸 이벤트)은 제외된다.
    @Query("SELECT se.activityClass AS activityClass, COUNT(se) AS total " +
           "FROM SensingEvent se " +
           "WHERE se.careTargetId = :ctId AND se.activityClass IS NOT NULL " +
           "AND se.detectedAt >= :from AND se.detectedAt < :to " +
           "GROUP BY se.activityClass")
    List<ActivityClassCount> countByActivityClass(@Param("ctId") Long careTargetId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to);

    interface RiskLevelCount {
        RiskLevel getRiskLevel();
        long getTotal();
    }

    interface ActivityClassCount {
        ActivityClass getActivityClass();
        long getTotal();
    }
}
