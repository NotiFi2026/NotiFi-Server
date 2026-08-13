package com.notifi.server.domain.report.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.caretarget.service.CareTargetAccessValidator;
import com.notifi.server.domain.report.dto.DailyMetricsResponse;
import com.notifi.server.domain.report.dto.DailyReportDetailResponse;
import com.notifi.server.domain.report.entity.DailyReport;
import com.notifi.server.domain.report.exception.ReportErrorCode;
import com.notifi.server.domain.report.repository.DailyReportRepository;
import com.notifi.server.domain.sensing.entity.ActivityClass;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.domain.sensing.repository.SensingEventRepository;
import com.notifi.server.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    @Mock DailyReportRepository dailyReportRepository;
    @Mock SensingEventRepository sensingEventRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock CareTargetAccessValidator careTargetAccessValidator;

    @InjectMocks ReportQueryService reportQueryService;

    private static final Long USER_ID = 7L;
    private static final Long CARE_TARGET_ID = 45L;
    private static final LocalDate DATE = LocalDate.of(2026, 8, 12);

    // ── P2 상세 · 권한 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getReport: 리포트가 매달린 노인에 대한 관계를 검증한 뒤 상세를 반환한다")
    void getReport_validatesRelationshipOfReportOwner() {
        given(dailyReportRepository.findById(210L)).willReturn(Optional.of(report()));

        DailyReportDetailResponse res = reportQueryService.getReport(USER_ID, 210L);

        then(careTargetAccessValidator).should().requireRelationshipOrSelf(USER_ID, CARE_TARGET_ID);
        assertThat(res.dailyReportId()).isEqualTo(210L);
        assertThat(res.careTargetId()).isEqualTo(CARE_TARGET_ID);
        assertThat(res.riskLevel()).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    @DisplayName("getReport: 없는 리포트는 404 — 권한 검증까지 가지 않는다")
    void getReport_missing_throws404() {
        given(dailyReportRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportQueryService.getReport(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_NOT_FOUND);

        then(careTargetAccessValidator).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getReport: 관계 없는 유저는 리포트 내용을 보지 못한다")
    void getReport_unrelatedUser_blocked() {
        given(dailyReportRepository.findById(210L)).willReturn(Optional.of(report()));
        willThrow(new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND))
                .given(careTargetAccessValidator).requireRelationshipOrSelf(USER_ID, CARE_TARGET_ID);

        assertThatThrownBy(() -> reportQueryService.getReport(USER_ID, 210L))
                .isInstanceOf(BusinessException.class);
    }

    // ── I6 집계 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDailyMetrics: 하루 경계를 KST로 자른다 — UTC로 자르면 리포트가 이틀에 걸친다")
    void getDailyMetrics_usesKstDayBoundary() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(true);
        given(sensingEventRepository.countByRiskLevel(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of());
        given(sensingEventRepository.countByActivityClass(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of());

        reportQueryService.getDailyMetrics(CARE_TARGET_ID, DATE);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        then(sensingEventRepository).should().countByRiskLevel(eq(CARE_TARGET_ID), from.capture(), to.capture());

        // 2026-08-12 KST 00:00 == 2026-08-11T15:00:00Z, 익일 00:00 == 2026-08-12T15:00:00Z
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-08-11T15:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-08-12T15:00:00Z"));
    }

    @Test
    @DisplayName("getDailyMetrics: warning·danger 건수와 activity_class 카운트를 반환한다")
    void getDailyMetrics_returnsCounts() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(true);
        given(sensingEventRepository.countByRiskLevel(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of(
                riskLevelCount(RiskLevel.SAFE, 812L),
                riskLevelCount(RiskLevel.WARNING, 3L),
                riskLevelCount(RiskLevel.DANGER, 1L)));
        given(sensingEventRepository.countByActivityClass(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of(
                activityClassCount(ActivityClass.WALKING, 120L),
                activityClassCount(ActivityClass.FALL_FROM_STANDING, 1L)));

        DailyMetricsResponse res = reportQueryService.getDailyMetrics(CARE_TARGET_ID, DATE);

        assertThat(res.warningEventCount()).isEqualTo(3L);
        assertThat(res.dangerEventCount()).isEqualTo(1L);
        // SAFE 건수는 응답에 직접 노출되지 않는다 — 세부 분류는 activity_class_counts가 담당
        assertThat(res.activityClassCounts()).containsExactlyInAnyOrderEntriesOf(
                Map.of("WALKING", 120L, "FALL_FROM_STANDING", 1L));
    }

    @Test
    @DisplayName("getDailyMetrics: 이벤트가 없는 날은 0으로 응답한다 (null 아님)")
    void getDailyMetrics_emptyDay_returnsZeros() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(true);
        given(sensingEventRepository.countByRiskLevel(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of());
        given(sensingEventRepository.countByActivityClass(eq(CARE_TARGET_ID), any(), any())).willReturn(List.of());

        DailyMetricsResponse res = reportQueryService.getDailyMetrics(CARE_TARGET_ID, DATE);

        assertThat(res.warningEventCount()).isZero();
        assertThat(res.dangerEventCount()).isZero();
        assertThat(res.activityClassCounts()).isEmpty();
    }

    @Test
    @DisplayName("getDailyMetrics: 존재하지 않는(또는 삭제된) 노인이면 404 — 집계 쿼리를 돌리지 않는다")
    void getDailyMetrics_unknownCareTarget_throws404() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(false);

        assertThatThrownBy(() -> reportQueryService.getDailyMetrics(CARE_TARGET_ID, DATE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CareTargetErrorCode.CARE_TARGET_NOT_FOUND);

        then(sensingEventRepository).should(never()).countByRiskLevel(any(), any(), any());
    }

    // ── fixture ─────────────────────────────────────────────────────────────

    private DailyReport report() {
        DailyReport r = DailyReport.create(
                CARE_TARGET_ID, DATE,
                List.of(Map.of("tag", "risk_event", "risk_level", "WARNING", "title", "제목")),
                Map.of("warning_event_count", 3),
                RiskLevel.WARNING, "제목", Instant.parse("2026-08-13T00:10:00Z"));
        ReflectionTestUtils.setField(r, "id", 210L);
        return r;
    }

    private SensingEventRepository.RiskLevelCount riskLevelCount(RiskLevel level, long total) {
        return new SensingEventRepository.RiskLevelCount() {
            @Override public RiskLevel getRiskLevel() { return level; }
            @Override public long getTotal() { return total; }
        };
    }

    private SensingEventRepository.ActivityClassCount activityClassCount(ActivityClass clazz, long total) {
        return new SensingEventRepository.ActivityClassCount() {
            @Override public ActivityClass getActivityClass() { return clazz; }
            @Override public long getTotal() { return total; }
        };
    }
}
