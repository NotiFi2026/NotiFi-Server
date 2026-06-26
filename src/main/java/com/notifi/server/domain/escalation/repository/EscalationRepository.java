package com.notifi.server.domain.escalation.repository;

import com.notifi.server.domain.escalation.entity.Escalation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EscalationRepository extends JpaRepository<Escalation, Long> {
    Optional<Escalation> findByRiskAssessmentId(Long riskAssessmentId);
}
