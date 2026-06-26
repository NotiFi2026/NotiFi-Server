package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {
    Optional<RiskAssessment> findBySensingEventId(Long sensingEventId);
}
