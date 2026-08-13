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
import com.notifi.server.global.exception.CommonErrorCode;
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

        SectionDigest digest = digestSections(req.sections());
        // AI 클라이언트가 generated_at을 드롭 중이라 없으면 수신 시각으로 채운다
        Instant generatedAt = req.generatedAt() != null ? req.generatedAt() : Instant.now();

        var existing = dailyReportRepository
                .findByCareTargetIdAndReportDate(req.careTargetId(), req.reportDate());
        if (existing.isPresent()) {
            existing.get().update(digest.sections(), req.metrics(),
                    digest.topRiskLevel(), digest.headline(), generatedAt);
            return new DailyReportIngestResponse(existing.get().getId(), req.careTargetId(), false);
        }

        DailyReport saved;
        try {
            saved = dailyReportRepository.save(DailyReport.create(
                    req.careTargetId(), req.reportDate(), digest.sections(), req.metrics(),
                    digest.topRiskLevel(), digest.headline(), generatedAt));
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
                saved.getId(), req.careTargetId(), req.reportDate(),
                digest.topRiskLevel(), digest.headline()));

        return new DailyReportIngestResponse(saved.getId(), req.careTargetId(), true);
    }

    /** 저장 직전의 sections와, 거기서 파생된 목록 카드용 두 값. */
    private record SectionDigest(
            List<Map<String, Object>> sections, RiskLevel topRiskLevel, String headline) {}

    /**
     * sections를 한 번 순회하며 저장 형태로 정리하고 목록 카드용 값을 함께 뽑는다.
     *
     * <p>세 가지를 한 패스에서 하는 이유는 셋이 같은 파싱 결과에 의존하기 때문이다.
     * 나눠 놓으면 risk_level을 두 번 파싱하게 되고, 미지의 값에 WARN 로그가 중복으로 찍힌다.
     *
     * <p>정리 규칙:
     * <ul>
     *   <li>AI는 risk_level을 소문자(`safe`)로 보내고 Spring·다른 API는 전부 대문자다. JSONB는
     *       서버가 검증하지 않아 표기 불일치가 그대로 앱까지 흘러가므로 적재 시점에 대문자로 통일한다.
     *   <li>원본 Map을 갈아엎지 않고 복사본을 만든다 — 요청 객체를 파괴하면 테스트·로깅이 거짓말을 한다.
     *   <li>null 원소는 건너뛴다. {@code @NotEmpty}는 리스트가 비었는지만 보므로 {@code [null]}이 통과한다.
     * </ul>
     */
    private SectionDigest digestSections(List<Map<String, Object>> rawSections) {
        List<Map<String, Object>> sections = new ArrayList<>(rawSections.size());
        RiskLevel top = RiskLevel.SAFE;
        String topTitle = null;       // 대표 등급을 만든 섹션의 title
        String firstTitle = null;     // 그 섹션에 title이 없을 때의 폴백
        boolean sawKnownLevel = false;
        boolean hasUnknownLevel = false;

        for (Map<String, Object> raw : rawSections) {
            if (raw == null) {
                continue;
            }
            Map<String, Object> section = new LinkedHashMap<>(raw);
            sections.add(section);

            String title = titleOf(section);
            if (firstTitle == null) {
                firstTitle = title;
            }

            Object rawLevel = section.get(SECTION_RISK_LEVEL);
            RiskLevel level = parseRiskLevel(rawLevel);
            if (level == null) {
                // 값이 아예 없는 것과 오타는 다르다 — 오타만 승격 사유로 센다
                hasUnknownLevel |= rawLevel != null;
                continue;
            }
            section.put(SECTION_RISK_LEVEL, level.name());

            // 카드의 제목과 배지가 같은 섹션을 가리키게 한다. 무조건 첫 섹션 title을 쓰면
            // "평온한 하루 / 주의"처럼 제목과 등급이 서로 다른 말을 하는 카드가 나온다.
            // 동급이 여럿이면 먼저 온 섹션이 이긴다.
            if (!sawKnownLevel || level.compareTo(top) > 0) {
                top = level;
                topTitle = title;
                sawKnownLevel = true;
            }
        }

        if (sections.isEmpty()) {
            // 살릴 섹션이 하나라도 있으면 적재하지만, 전부 비어 있으면 리포트가 아니다
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        if (hasUnknownLevel && top == RiskLevel.SAFE) {
            // 모르는 등급을 SAFE로 확정하면 위험을 낮게 표기한다. AI가 새 등급(CRITICAL 등)을
            // 추가했을 때 위험한 리포트가 카드에 "안전"으로 뜨는 쪽이 반대보다 훨씬 나쁘다.
            // top_risk_level은 표시 전용이라 승격이 에스컬레이션을 만들지는 않는다.
            log.warn("[I3] 알 수 없는 risk_level이 있어 대표 등급을 WARNING으로 승격한다");
            top = RiskLevel.WARNING;
        }

        return new SectionDigest(sections, top, topTitle != null ? topTitle : firstTitle);
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

    /** 카드 제목 후보. 컬럼 길이(200)를 넘으면 자르고, 비어 있으면 null. */
    private String titleOf(Map<String, Object> section) {
        if (!(section.get(SECTION_TITLE) instanceof String s) || s.isBlank()) {
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
