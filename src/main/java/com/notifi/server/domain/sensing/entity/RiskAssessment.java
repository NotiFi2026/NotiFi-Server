package com.notifi.server.domain.sensing.entity;

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
@Table(name = "tb_risk_assessment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_assessment_id")
    private Long id;

    @Column(name = "sensing_event_id", nullable = false, unique = true)
    private Long sensingEventId;

    @Column(name = "risk_score", nullable = false)
    private short riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "JSONB")
    private Map<String, Object> scoreBreakdown;

    @Column(name = "model_version", nullable = false, length = 30)
    private String modelVersion;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static RiskAssessment of(Long sensingEventId, short riskScore, RiskLevel riskLevel,
                                    Map<String, Object> scoreBreakdown, String modelVersion,
                                    Instant assessedAt) {
        RiskAssessment ra = new RiskAssessment();
        ra.sensingEventId = sensingEventId;
        ra.riskScore = riskScore;
        ra.riskLevel = riskLevel;
        ra.scoreBreakdown = scoreBreakdown;
        ra.modelVersion = modelVersion;
        ra.assessedAt = assessedAt;
        return ra;
    }
}
