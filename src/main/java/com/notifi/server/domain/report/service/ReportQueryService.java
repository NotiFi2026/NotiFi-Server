package com.notifi.server.domain.report.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.report.dto.DailyMetricsResponse;
import com.notifi.server.domain.report.dto.DailyReportDetailResponse;
import com.notifi.server.domain.report.dto.DailyReportSummaryResponse;
import com.notifi.server.domain.report.entity.DailyReport;
import com.notifi.server.domain.report.exception.ReportErrorCode;
import com.notifi.server.domain.report.repository.DailyReportRepository;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import com.notifi.server.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P1·P2(보호자 조회)와 I6(AI 집계 조회).
 */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    /**
     * 리포트의 "하루" 경계. detected_at은 UTC로 저장되지만 report_date는 생활 시간 기준이어야 한다
     * — UTC로 자르면 한국 시간 09:00에 날짜가 바뀌어 리포트가 이틀에 걸친다.
     */
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Seoul");

    private final DailyReportRepository dailyReportRepository;
    private final SensingEventRepository sensingEventRepository;
    private final CareTargetRepository careTargetRepository;
    private final CareTargetAccessValidator careTargetAccessValidator;

    /** P1 — 노인별 리포트 목록. */
    @Transactional(readOnly = true)
    public PageResponse<DailyReportSummaryResponse> getReports(Long userId, Long careTargetId, Pageable pageable) {
        // 노인 본인도 자기 리포트를 본다
        careTargetAccessValidator.requireRelationshipOrSelf(userId, careTargetId);
        return PageResponse.from(dailyReportRepository.findSummaries(careTargetId, pageable));
    }

    /** P2 — 리포트 상세. 리포트가 매달린 노인에 대한 관계가 있어야 한다. */
    @Transactional(readOnly = true)
    public DailyReportDetailResponse getReport(Long userId, Long dailyReportId) {
        DailyReport report = dailyReportRepository.findById(dailyReportId)
                .orElseThrow(() -> new BusinessException(ReportErrorCode.REPORT_NOT_FOUND));
        careTargetAccessValidator.requireRelationshipOrSelf(userId, report.getCareTargetId());
        return DailyReportDetailResponse.of(report);
    }

    /**
     * I6 — 하루치 이벤트 집계. AI가 저장소 없이 리포트 수치를 얻는 유일한 경로다.
     * Spring이 저장한 이벤트와 리포트 수치가 어긋나지 않도록 카운트의 단일 출처를 여기로 둔다.
     */
    @Transactional(readOnly = true)
    public DailyMetricsResponse getDailyMetrics(Long careTargetId, LocalDate date) {
        if (!careTargetRepository.existsById(careTargetId)) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
        }

        Instant from = date.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        Map<RiskLevel, Long> byRiskLevel = new LinkedHashMap<>();
        for (var row : sensingEventRepository.countByRiskLevel(careTargetId, from, to)) {
            byRiskLevel.put(row.getRiskLevel(), row.getTotal());
        }

        Map<String, Long> activityClassCounts = new LinkedHashMap<>();
        for (var row : sensingEventRepository.countByActivityClass(careTargetId, from, to)) {
            activityClassCounts.put(row.getActivityClass().name(), row.getTotal());
        }

        return new DailyMetricsResponse(
                careTargetId,
                date,
                byRiskLevel.getOrDefault(RiskLevel.WARNING, 0L),
                byRiskLevel.getOrDefault(RiskLevel.DANGER, 0L),
                activityClassCounts
        );
    }
}
