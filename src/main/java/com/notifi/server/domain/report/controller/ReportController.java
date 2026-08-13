package com.notifi.server.domain.report.controller;

import com.notifi.server.domain.report.dto.DailyReportDetailResponse;
import com.notifi.server.domain.report.dto.DailyReportSummaryResponse;
import com.notifi.server.domain.report.service.ReportQueryService;
import com.notifi.server.global.response.ApiResponse;
import com.notifi.server.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Report", description = "LLM 생성 일일 리포트 조회")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportQueryService reportQueryService;

    @Operation(summary = "[P1] 일일 리포트 목록",
               description = "노인의 일일 리포트를 최신순으로 반환한다. 목록 카드는 sections 전문 대신 " +
                             "대표 위험도(risk_level = 섹션 중 최고 등급)와 headline(첫 섹션 title)만 싣는다 — 전문은 P2. (권한: 관계)")
    @GetMapping("/care-targets/{id}/reports")
    public ApiResponse<PageResponse<DailyReportSummaryResponse>> getReports(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 무효 정렬 프로퍼티로 인한 500 방지 — 서버가 report_date DESC 고정
        Pageable safe = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "reportDate"));
        return ApiResponse.success(reportQueryService.getReports(userId, id, safe));
    }

    @Operation(summary = "[P2] 리포트 상세",
               description = "리포트 전문을 반환한다. sections[]는 tag·risk_level·title·body·recommended_action 구조이며 " +
                             "risk_level은 대문자로 정규화되어 있다. metrics는 AI가 보낸 자유 구조 그대로다. (권한: 관계)")
    @GetMapping("/reports/{id}")
    public ApiResponse<DailyReportDetailResponse> getReport(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id
    ) {
        return ApiResponse.success(reportQueryService.getReport(userId, id));
    }
}
