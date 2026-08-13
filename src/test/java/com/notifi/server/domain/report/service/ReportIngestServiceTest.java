package com.notifi.server.domain.report.service;

import com.notifi.server.domain.caretarget.exception.CareTargetErrorCode;
import com.notifi.server.domain.caretarget.repository.CareTargetRepository;
import com.notifi.server.domain.report.dto.DailyReportIngestRequest;
import com.notifi.server.domain.report.dto.DailyReportIngestResponse;
import com.notifi.server.domain.report.entity.DailyReport;
import com.notifi.server.domain.report.event.DailyReportSavedEvent;
import com.notifi.server.domain.report.exception.ReportErrorCode;
import com.notifi.server.domain.report.repository.DailyReportRepository;
import com.notifi.server.domain.sensing.entity.RiskLevel;
import com.notifi.server.global.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReportIngestServiceTest {

    @Mock DailyReportRepository dailyReportRepository;
    @Mock CareTargetRepository careTargetRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks ReportIngestService reportIngestService;

    private static final Long CARE_TARGET_ID = 45L;
    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 12);
    private static final Instant GENERATED_AT = Instant.parse("2026-08-13T00:10:00Z");

    // ── 신규 적재 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingest: 신규 리포트는 저장 후 created=true와 알림 이벤트를 낸다")
    void ingest_new_savesAndPublishes() {
        givenNoExistingReport();
        givenSavedWithId(210L);

        DailyReportIngestResponse res = reportIngestService.ingest(request(sections(
                section("safe", "평소와 비슷한 하루였어요"))));

        assertThat(res.dailyReportId()).isEqualTo(210L);
        assertThat(res.created()).isTrue();

        ArgumentCaptor<DailyReportSavedEvent> captor = ArgumentCaptor.forClass(DailyReportSavedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().dailyReportId()).isEqualTo(210L);
        assertThat(captor.getValue().careTargetId()).isEqualTo(CARE_TARGET_ID);
        assertThat(captor.getValue().headline()).isEqualTo("평소와 비슷한 하루였어요");
    }

    @Test
    @DisplayName("ingest: 존재하지 않는(또는 삭제된) 노인이면 404")
    void ingest_unknownCareTarget_throws404() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(false);

        assertThatThrownBy(() -> reportIngestService.ingest(request(sections(section("safe", "제목")))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CareTargetErrorCode.CARE_TARGET_NOT_FOUND);

        then(dailyReportRepository).should(never()).save(any());
    }

    // ── 재적재(UPSERT) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ingest: 같은 (노인, 날짜) 재적재는 기존 행을 갱신하고 새 행·푸시를 만들지 않는다")
    void ingest_duplicate_updatesWithoutNotification() {
        DailyReport existing = DailyReport.create(
                CARE_TARGET_ID, REPORT_DATE, sections(section("SAFE", "이전 제목")),
                Map.of(), RiskLevel.SAFE, "이전 제목", GENERATED_AT);
        ReflectionTestUtils.setField(existing, "id", 210L);

        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(true);
        given(dailyReportRepository.findByCareTargetIdAndReportDate(CARE_TARGET_ID, REPORT_DATE))
                .willReturn(Optional.of(existing));

        DailyReportIngestResponse res = reportIngestService.ingest(request(sections(
                section("danger", "낙상이 감지됐어요"))));

        assertThat(res.dailyReportId()).isEqualTo(210L);
        assertThat(res.created()).isFalse();
        assertThat(existing.getHeadline()).isEqualTo("낙상이 감지됐어요");
        assertThat(existing.getTopRiskLevel()).isEqualTo(RiskLevel.DANGER);

        then(dailyReportRepository).should(never()).save(any());
        // 재시도마다 보호자 폰이 울면 안 된다
        then(eventPublisher).should(never()).publishEvent(any(DailyReportSavedEvent.class));
    }

    @Test
    @DisplayName("ingest: 동시 적재 경합은 409 — 재시도가 갱신 경로로 수렴한다")
    void ingest_concurrentInsert_throws409() {
        givenNoExistingReport();
        given(dailyReportRepository.save(any())).willThrow(constraintViolation("uq_daily_report_target_date"));

        assertThatThrownBy(() -> reportIngestService.ingest(request(sections(section("safe", "제목")))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReportErrorCode.REPORT_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("ingest: 다른 제약 위반은 중복으로 오보고하지 않고 그대로 전파한다")
    void ingest_otherConstraint_propagates() {
        givenNoExistingReport();
        given(dailyReportRepository.save(any())).willThrow(constraintViolation("fk_daily_report_care_target"));

        assertThatThrownBy(() -> reportIngestService.ingest(request(sections(section("safe", "제목")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── risk_level 정규화 · 대표 등급 ───────────────────────────────────────

    @Test
    @DisplayName("ingest: AI가 보낸 소문자 risk_level을 대문자로 정규화해 저장한다")
    void ingest_lowercaseRiskLevel_normalizedToUpperCase() {
        givenNoExistingReport();
        givenSavedWithId(1L);

        reportIngestService.ingest(request(sections(
                section("warning", "불안정한 보행이 있었어요"))));

        DailyReport saved = captureSaved();
        assertThat(saved.getSections().get(0).get("risk_level")).isEqualTo("WARNING");
        assertThat(saved.getTopRiskLevel()).isEqualTo(RiskLevel.WARNING);
    }

    @Test
    @DisplayName("ingest: 대표 등급은 섹션 중 최고 위험도다")
    void ingest_topRiskLevel_isMaxAcrossSections() {
        givenNoExistingReport();
        givenSavedWithId(1L);

        reportIngestService.ingest(request(sections(
                section("safe", "첫 섹션"),
                section("danger", "두 번째 섹션"),
                section("warning", "세 번째 섹션"))));

        DailyReport saved = captureSaved();
        assertThat(saved.getTopRiskLevel()).isEqualTo(RiskLevel.DANGER);
        // 카드 제목은 최고 등급 섹션이 아니라 첫 섹션 title이다
        assertThat(saved.getHeadline()).isEqualTo("첫 섹션");
    }

    @Test
    @DisplayName("ingest: 알 수 없는 risk_level은 리포트를 거부하지 않고 무시한다")
    void ingest_unknownRiskLevel_isIgnored() {
        givenNoExistingReport();
        givenSavedWithId(1L);

        reportIngestService.ingest(request(sections(section("critical", "오타 섹션"))));

        DailyReport saved = captureSaved();
        // 원본 값이 보존되고(정규화 실패) 대표 등급은 SAFE로 떨어진다
        assertThat(saved.getSections().get(0).get("risk_level")).isEqualTo("critical");
        assertThat(saved.getTopRiskLevel()).isEqualTo(RiskLevel.SAFE);
    }

    @Test
    @DisplayName("ingest: 요청 sections 원본 Map을 변형하지 않는다")
    void ingest_doesNotMutateRequestSections() {
        givenNoExistingReport();
        givenSavedWithId(1L);

        List<Map<String, Object>> original = sections(section("safe", "제목"));
        reportIngestService.ingest(request(original));

        assertThat(original.get(0).get("risk_level")).isEqualTo("safe");
    }

    // ── generated_at ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ingest: generated_at이 없으면 서버 수신 시각으로 채운다")
    void ingest_missingGeneratedAt_fallsBackToNow() {
        givenNoExistingReport();
        givenSavedWithId(1L);
        Instant before = Instant.now();

        reportIngestService.ingest(new DailyReportIngestRequest(
                CARE_TARGET_ID, REPORT_DATE, sections(section("safe", "제목")), Map.of(), null));

        assertThat(captureSaved().getGeneratedAt()).isAfterOrEqualTo(before);
    }

    // ── fixture ─────────────────────────────────────────────────────────────

    private void givenNoExistingReport() {
        given(careTargetRepository.existsById(CARE_TARGET_ID)).willReturn(true);
        given(dailyReportRepository.findByCareTargetIdAndReportDate(CARE_TARGET_ID, REPORT_DATE))
                .willReturn(Optional.empty());
    }

    private void givenSavedWithId(Long id) {
        given(dailyReportRepository.save(any())).willAnswer(inv -> {
            DailyReport arg = inv.getArgument(0);
            ReflectionTestUtils.setField(arg, "id", id);
            return arg;
        });
    }

    private DailyReport captureSaved() {
        ArgumentCaptor<DailyReport> captor = ArgumentCaptor.forClass(DailyReport.class);
        then(dailyReportRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private DailyReportIngestRequest request(List<Map<String, Object>> sections) {
        return new DailyReportIngestRequest(
                CARE_TARGET_ID, REPORT_DATE, sections,
                Map.of("warning_event_count", 1, "danger_event_count", 0), GENERATED_AT);
    }

    @SafeVarargs
    private List<Map<String, Object>> sections(Map<String, Object>... items) {
        return new java.util.ArrayList<>(List.of(items));
    }

    private Map<String, Object> section(String riskLevel, String title) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("tag", "risk_event");
        section.put("risk_level", riskLevel);
        section.put("title", title);
        section.put("body", "본문");
        section.put("recommended_action", null);
        return section;
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("conflict",
                new ConstraintViolationException("conflict", null, constraintName));
    }
}
