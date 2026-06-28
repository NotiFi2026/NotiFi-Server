package com.notifi.server.domain.escalation.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "tb_escalation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Escalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escalation_id")
    private Long id;

    @Column(name = "risk_assessment_id", nullable = false)
    private Long riskAssessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscalationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", length = 30)
    private ResolutionType resolutionType;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "resolution_memo", columnDefinition = "TEXT")
    private String resolutionMemo;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public static Escalation start(Long riskAssessmentId) {
        Escalation e = new Escalation();
        e.riskAssessmentId = riskAssessmentId;
        e.status = EscalationStatus.IN_PROGRESS;
        e.startedAt = Instant.now();
        return e;
    }
}
