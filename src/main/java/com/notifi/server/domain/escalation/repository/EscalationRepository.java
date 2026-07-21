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
}
