package com.notifi.server.domain.report.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * I6 응답. AI가 무상태를 유지한 채 하루치 카운트를 얻는 유일한 경로다.
 *
 * <p>필드명이 AI의 {@code DailyReportMetrics.safe_class_counts}와 다른 건 의도적이다 —
 * activity_class 17종에는 위험 클래스(FALL_*)도 들어 있어 "safe"가 아니다.
 * AI가 어느 필드에 담을지는 그쪽 자유.
 */
public record DailyMetricsResponse(
        Long careTargetId,
        LocalDate date,
        long warningEventCount,
        long dangerEventCount,
        Map<String, Long> activityClassCounts
) {}
