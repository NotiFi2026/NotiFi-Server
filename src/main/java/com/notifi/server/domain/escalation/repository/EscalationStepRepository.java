package com.notifi.server.domain.escalation.repository;

import com.notifi.server.domain.escalation.entity.EscalationStep;
import com.notifi.server.domain.escalation.entity.StepType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EscalationStepRepository extends JpaRepository<EscalationStep, Long> {

    Optional<EscalationStep> findByEscalationIdAndStepType(Long escalationId, StepType stepType);

    // E2: 에스컬레이션 상세 — 단계별 진행 로그 (step_order 오름차순)
    List<EscalationStep> findByEscalationIdOrderByStepOrderAsc(Long escalationId);
}
