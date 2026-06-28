package com.notifi.server.domain.escalation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tb_escalation_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscalationStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long id;

    @Column(name = "escalation_id", nullable = false)
    private Long escalationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 30)
    private StepType stepType;

    @Column(name = "step_order", nullable = false)
    private short stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StepStatus status;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_detail", columnDefinition = "JSONB")
    private Map<String, Object> responseDetail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static EscalationStep record(Long escalationId, StepType stepType, short stepOrder,
                                        StepStatus status, Instant executedAt, Instant respondedAt,
                                        Map<String, Object> responseDetail) {
        EscalationStep step = new EscalationStep();
        step.escalationId = escalationId;
        step.stepType = stepType;
        step.stepOrder = stepOrder;
        step.status = status;
        step.executedAt = executedAt;
        step.respondedAt = respondedAt;
        step.responseDetail = responseDetail;
        return step;
    }

    public void updateProgress(StepStatus status, Instant executedAt,
                               Instant respondedAt, Map<String, Object> responseDetail) {
        this.status = status;
        this.executedAt = executedAt;
        this.respondedAt = respondedAt;
        this.responseDetail = responseDetail;
    }
}
