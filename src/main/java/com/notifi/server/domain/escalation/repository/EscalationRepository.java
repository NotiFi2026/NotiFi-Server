package com.notifi.server.domain.escalation.repository;

import com.notifi.server.domain.escalation.entity.Escalation;
import com.notifi.server.domain.escalation.entity.EscalationStatus;
import com.notifi.server.domain.sensing.entity.RiskAssessment;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {

    Optional<Escalation> findByRiskAssessmentId(Long riskAssessmentId);

    // I2 recordStep ↔ E3 resolve 직렬화용 행 잠금 — 해소 직후 EMERGENCY_CALL이
    // 낡은 IN_PROGRESS 판단으로 EXECUTED 기록되는 경쟁 상태 차단
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Escalation e WHERE e.id = :id")
    Optional<Escalation> findByIdForUpdate(@Param("id") Long id);

    // E1: care_target 기준 목록 — Long id 컬럼 연관이므로 theta-join
    @Query(value = "SELECT e FROM Escalation e, RiskAssessment ra, SensingEvent se "
            + "WHERE e.riskAssessmentId = ra.id AND ra.sensingEventId = se.id "
            + "AND se.careTargetId = :ctId ORDER BY e.startedAt DESC",
           countQuery = "SELECT COUNT(e) FROM Escalation e, RiskAssessment ra, SensingEvent se "
            + "WHERE e.riskAssessmentId = ra.id AND ra.sensingEventId = se.id "
            + "AND se.careTargetId = :ctId")
    Page<Escalation> findByCareTargetId(@Param("ctId") Long careTargetId, Pageable pageable);

    // S1: 진행 중 에스컬레이션 최신 순 조회 — E1과 동일한 theta-join 패턴
    @Query("SELECT e FROM Escalation e, RiskAssessment ra, SensingEvent se "
            + "WHERE e.riskAssessmentId = ra.id AND ra.sensingEventId = se.id "
            + "AND se.careTargetId = :ctId AND e.status = :status ORDER BY e.startedAt DESC")
    List<Escalation> findByCareTargetIdAndStatus(@Param("ctId") Long careTargetId,
                                                 @Param("status") EscalationStatus status,
                                                 Pageable pageable);

    /**
     * C2 목록에서 "응급이 진행 중인 노인"을 한 번에 가려낸다.
     *
     * <p>노인마다 {@link #findByCareTargetIdAndStatus}를 부르면 목록 크기만큼 쿼리가 늘어난다.
     * 표시용 보정 하나 때문에 N+1을 만들 이유가 없다.
     */
    @Query("SELECT DISTINCT se.careTargetId FROM Escalation e, RiskAssessment ra, SensingEvent se "
            + "WHERE e.riskAssessmentId = ra.id AND ra.sensingEventId = se.id "
            + "AND se.careTargetId IN :ctIds AND e.status = :status")
    List<Long> findCareTargetIdsWithStatus(@Param("ctIds") Collection<Long> careTargetIds,
                                           @Param("status") EscalationStatus status);

    /**
     * I1이 새 에스컬레이션을 만들기 전에 확인하는 "지금 대응이 돌고 있는가".
     *
     * <p><b>{@code since} 이후에 시작된 건만 본다.</b> 119 단계까지 간 에스컬레이션은 사람이
     * 앱에서 닫을 때까지 IN_PROGRESS로 남는 것이 정상 설계다. 그걸 기한 없이 "대응 중"으로
     * 취급하면 몇 시간 뒤의 <b>진짜 낙상이 조용히 무시된다</b> — 막으려던 것보다 훨씬 나쁘다.
     *
     * <p>가장 먼저 시작된 것을 잡는다 — 재사용할 때 대응 흐름이 갈라지지 않게.
     */
    @Query("SELECT e FROM Escalation e, RiskAssessment ra, SensingEvent se "
            + "WHERE e.riskAssessmentId = ra.id AND ra.sensingEventId = se.id "
            + "AND se.careTargetId = :ctId AND e.status = :status AND e.startedAt >= :since "
            + "ORDER BY e.startedAt ASC")
    List<Escalation> findOngoingSince(@Param("ctId") Long careTargetId,
                                      @Param("status") EscalationStatus status,
                                      @Param("since") Instant since,
                                      Pageable pageable);
}
