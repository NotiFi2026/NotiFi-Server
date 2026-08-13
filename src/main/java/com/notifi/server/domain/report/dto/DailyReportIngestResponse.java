package com.notifi.server.domain.report.dto;

public record DailyReportIngestResponse(
        Long dailyReportId,
        Long careTargetId,
        boolean created // false면 기존 리포트를 갱신한 것 (재적재)
) {}
