package com.notifi.server.domain.escalation.repository;

import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EscalationStepRepository extends JpaRepository<EscalationStep, Long> {

    Optional<EscalationStep> findByEscalationIdAndStepType(Long escalationId, StepType stepType);
}
