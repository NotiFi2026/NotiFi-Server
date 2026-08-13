package com.notifi.server.domain.report.controller;

import com.notifi.server.domain.report.dto.DailyMetricsResponse;
import com.notifi.server.domain.report.dto.DailyReportIngestRequest;
import com.notifi.server.domain.report.dto.DailyReportIngestResponse;
import com.notifi.server.domain.report.service.ReportIngestService;
import com.notifi.server.domain.report.service.ReportQueryService;
import com.notifi.server.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Internal Report", description = "AI 서버 → Spring 내부 통신 — 일일 리포트 적재·집계 조회")
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalReportController {

    private final ReportIngestService reportIngestService;
    private final ReportQueryService reportQueryService;

    @Operation(
            summary = "[I3] 일일 리포트 적재",
            description = "LLM이 생성한 일일 리포트를 적재한다. (care_target_id, report_date) 기준 UPSERT — 신규 생성은 201, 기존 리포트 갱신은 200을 반환한다(응답의 created와 일치). " +
                          "sections[].risk_level은 소문자로 와도 대문자(SAFE/WARNING/DANGER)로 정규화해 저장한다. 알 수 없는 등급이 섞여 있으면 거부하지 않고 대표 등급을 WARNING으로 승격한다. " +
                          "generated_at은 선택이며 없으면 서버 수신 시각으로 채운다. " +
                          "신규 생성일 때만 보호자에게 DAILY_REPORT 알림·FCM을 발송한다(재적재는 무푸시, 응급이 아닌 일반 채널). (권한: X-Internal-Key)"
    )
    // 이 엔드포인트만 ResponseEntity를 쓴다 — 상태코드가 적재 결과(신규/갱신)에 따라 갈리므로
    // 정적인 @ResponseStatus로는 표현할 수 없다. 나머지 엔드포인트는 기존 컨벤션을 유지한다.
    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<DailyReportIngestResponse>> ingest(
            @Valid @RequestBody DailyReportIngestRequest request
    ) {
        DailyReportIngestResponse result = reportIngestService.ingest(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }

    @Operation(
            summary = "[I6] 일일 집계 조회",
            description = "리포트 생성에 필요한 하루치 카운트를 반환한다. AI 서버가 저장소 없이 수치를 얻는 경로로, Spring DB가 카운트의 단일 출처다. " +
                          "하루 경계는 Asia/Seoul 기준 [00:00, 익일 00:00)이다. " +
                          "activity_class_counts는 대문자 17종 키이며 activity_class가 없는 이벤트는 집계에서 빠진다. (권한: X-Internal-Key)"
    )
    @GetMapping("/care-targets/{id}/daily-metrics")
    public ApiResponse<DailyMetricsResponse> getDailyMetrics(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(reportQueryService.getDailyMetrics(id, date));
    }
}
