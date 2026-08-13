package com.notifi.server.domain.report.dto;

import com.notifi.server.domain.report.entity.DailyReport;
import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;

/**
 * P1 목록 카드. sections 전문 대신 대표 등급·제목만 싣는다 — 상세는 P2로 간다.
 */
public record DailyReportSummaryResponse(
        Long dailyReportId,
        LocalDate reportDate,
        RiskLevel riskLevel,
        String headline,
        Instant generatedAt
) {
    public static DailyReportSummaryResponse of(DailyReport r) {
        return new DailyReportSummaryResponse(
                r.getId(),
                r.getReportDate(),
                r.getTopRiskLevel(),
                r.getHeadline(),
                r.getGeneratedAt()
        );
    }
}
