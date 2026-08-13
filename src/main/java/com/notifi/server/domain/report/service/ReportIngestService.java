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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.notifi.server.domain.report.dto.DailyReportIngestRequest.SECTION_RISK_LEVEL;
import static com.notifi.server.domain.report.dto.DailyReportIngestRequest.SECTION_TITLE;

/**
 * I3 — LLM 생성 일일 리포트 적재. (care_target_id, report_date) 기준 UPSERT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportIngestService {

    private static final String UQ_TARGET_DATE = "uq_daily_report_target_date";
    private static final int HEADLINE_MAX_LENGTH = 200;

    private final DailyReportRepository dailyReportRepository;
    private final CareTargetRepository careTargetRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DailyReportIngestResponse ingest(DailyReportIngestRequest req) {
        // @SQLRestriction("deleted_at IS NULL") — soft-deleted 노인은 여기서 404
        if (!careTargetRepository.existsById(req.careTargetId())) {
            throw new BusinessException(CareTargetErrorCode.CARE_TARGET_NOT_FOUND);
        }

        List<Map<String, Object>> sections = normalizeSections(req.sections());
        RiskLevel topRiskLevel = topRiskLevelOf(sections);
        String headline = headlineOf(sections);
        // AI 클라이언트가 generated_at을 드롭 중이라 없으면 수신 시각으로 채운다
        Instant generatedAt = req.generatedAt() != null ? req.generatedAt() : Instant.now();

        var existing = dailyReportRepository
                .findByCareTargetIdAndReportDate(req.careTargetId(), req.reportDate());
        if (existing.isPresent()) {
            existing.get().update(sections, req.metrics(), topRiskLevel, headline, generatedAt);
            return new DailyReportIngestResponse(existing.get().getId(), req.careTargetId(), false);
        }

        DailyReport saved;
        try {
            saved = dailyReportRepository.save(DailyReport.create(
                    req.careTargetId(), req.reportDate(), sections, req.metrics(),
                    topRiskLevel, headline, generatedAt));
        } catch (DataIntegrityViolationException e) {
            // 동시 적재 경합만 409 — FK·CHECK 위반까지 삼키지 않도록 제약명으로 한정한다(I1·I5와 동일 규칙).
            //
            // 여기서 기존 행 갱신으로 이어붙이지 않는 이유: INSERT가 제약 위반으로 터진 시점에
            // 이 트랜잭션은 이미 rollback-only다. 같은 트랜잭션에서 update를 시도하면
            // 커밋 때 UnexpectedRollbackException이 난다. 재시도가 위 update 경로로 수렴하므로
            // 409를 받은 AI가 한 번 더 보내면 그대로 갱신된다.
            if (isConstraintViolation(e, UQ_TARGET_DATE)) {
                throw new BusinessException(ReportErrorCode.REPORT_ALREADY_EXISTS);
            }
            throw e;
        }

        // 신규 생성일 때만 발행 — 재적재마다 보호자 폰이 울리면 안 된다
        eventPublisher.publishEvent(new DailyReportSavedEvent(
                saved.getId(), req.careTargetId(), req.reportDate(), topRiskLevel, headline));

        return new DailyReportIngestResponse(saved.getId(), req.careTargetId(), true);
    }

    /**
     * sections를 저장 직전 형태로 정리한다.
     *
     * <p>AI는 risk_level을 소문자(`safe`)로 보내고 Spring·다른 API는 전부 대문자다.
     * JSONB는 서버가 검증하지 않아 표기 불일치가 그대로 앱까지 흘러가므로 적재 시점에 대문자로 통일한다.
     * 원본 Map을 갈아엎지 않고 복사본을 만든다 — 요청 객체를 파괴하면 테스트·로깅이 거짓말을 하게 된다.
     */
    private List<Map<String, Object>> normalizeSections(List<Map<String, Object>> sections) {
        List<Map<String, Object>> normalized = new ArrayList<>(sections.size());
        for (Map<String, Object> section : sections) {
            Map<String, Object> copy = new LinkedHashMap<>(section);
            RiskLevel level = parseRiskLevel(copy.get(SECTION_RISK_LEVEL));
            if (level != null) {
                copy.put(SECTION_RISK_LEVEL, level.name());
            }
            normalized.add(copy);
        }
        return normalized;
    }

    /**
     * 관대 바인딩 — 미지의 값은 거부하지 않고 null로 흘린다(ActivityClass.from과 같은 이유).
     * 리포트 한 섹션의 오타가 하루치 리포트 전체를 400으로 막으면 안 된다.
     */
    private RiskLevel parseRiskLevel(Object raw) {
        if (!(raw instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return RiskLevel.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("[I3] 알 수 없는 section risk_level 값 무시: {}", s);
            return null;
        }
    }

    /**
     * 목록 카드용 대표 등급 = 섹션 중 최고 위험도. 판정할 값이 하나도 없으면 SAFE.
     * RiskLevel의 선언 순서(SAFE→WARNING→DANGER)가 곧 심각도 순서다.
     */
    private RiskLevel topRiskLevelOf(List<Map<String, Object>> sections) {
        RiskLevel top = RiskLevel.SAFE;
        for (Map<String, Object> section : sections) {
            RiskLevel level = parseRiskLevel(section.get(SECTION_RISK_LEVEL));
            if (level != null && level.compareTo(top) > 0) {
                top = level;
            }
        }
        return top;
    }

    /** 목록 카드 제목 = 첫 섹션 title. 컬럼 길이(200)를 넘으면 자른다. */
    private String headlineOf(List<Map<String, Object>> sections) {
        Object title = sections.get(0).get(SECTION_TITLE);
        if (!(title instanceof String s) || s.isBlank()) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.length() <= HEADLINE_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, HEADLINE_MAX_LENGTH);
    }

    private static boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        return e.getCause() instanceof ConstraintViolationException cve
                && constraintName.equalsIgnoreCase(cve.getConstraintName());
    }
}
