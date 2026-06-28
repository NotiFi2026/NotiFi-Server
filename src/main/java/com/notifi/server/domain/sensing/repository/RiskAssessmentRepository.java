package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findBySensingEventId(Long sensingEventId);

    // S2: N+1 방지 — 이벤트 id 목록으로 위험도 일괄 로드
    List<RiskAssessment> findBySensingEventIdIn(Collection<Long> sensingEventIds);
}
