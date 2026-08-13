package com.notifi.server.domain.report.entity;

import com.notifi.server.domain.sensing.entity.RiskLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 노인·일자별 1건의 LLM 생성 리포트. I3로 적재(UPSERT)하고 P1·P2로 조회한다.
 *
 * <p>{@code sections}는 자유 구조 JSONB다 — AI가 태그를 늘려도 스키마가 따라 바뀌지 않는다.
 * 대신 목록(P1)에 필요한 두 값만 {@code topRiskLevel}·{@code headline} 컬럼으로 뽑아 둔다.
 */
@Entity
@Table(name = "tb_daily_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_report_id")
    private Long id;

    @Column(name = "care_target_id", nullable = false)
    private Long careTargetId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sections", nullable = false, columnDefinition = "JSONB")
    private List<Map<String, Object>> sections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "JSONB")
    private Map<String, Object> metrics;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_risk_level", nullable = false, length = 20)
    private RiskLevel topRiskLevel;

    @Column(name = "headline", length = 200)
    private String headline;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static DailyReport create(Long careTargetId, LocalDate reportDate,
                                     List<Map<String, Object>> sections, Map<String, Object> metrics,
                                     RiskLevel topRiskLevel, String headline, Instant generatedAt) {
        DailyReport r = new DailyReport();
        r.careTargetId = careTargetId;
        r.reportDate = reportDate;
        r.apply(sections, metrics, topRiskLevel, headline, generatedAt);
        return r;
    }

    /** 재적재(UPSERT) — 식별자인 careTargetId·reportDate는 그대로 두고 내용만 갈아끼운다. */
    public void update(List<Map<String, Object>> sections, Map<String, Object> metrics,
                       RiskLevel topRiskLevel, String headline, Instant generatedAt) {
        apply(sections, metrics, topRiskLevel, headline, generatedAt);
    }

    private void apply(List<Map<String, Object>> sections, Map<String, Object> metrics,
                       RiskLevel topRiskLevel, String headline, Instant generatedAt) {
        this.sections = sections;
        this.metrics = metrics;
        this.topRiskLevel = topRiskLevel;
        this.headline = headline;
        this.generatedAt = generatedAt;
    }
}
