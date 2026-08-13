package com.notifi.server.domain.report.dto;

import com.notifi.server.domain.report.entity.DailyReport;
import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * P2 상세. sections는 적재된 그대로(단, risk_level은 대문자 정규화된 상태) 반환한다.
 */
public record DailyReportDetailResponse(
        Long dailyReportId,
        Long careTargetId,
        LocalDate reportDate,
        RiskLevel riskLevel,
        List<Map<String, Object>> sections,
        Map<String, Object> metrics,
        Instant generatedAt
) {
    public static DailyReportDetailResponse of(DailyReport r) {
        return new DailyReportDetailResponse(
                r.getId(),
                r.getCareTargetId(),
                r.getReportDate(),
                r.getTopRiskLevel(),
                r.getSections(),
                r.getMetrics(),
                r.getGeneratedAt()
        );
    }
}
