package com.notifi.server.domain.report.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * I3 적재 요청. AI {@code DailyReportOutput}(app/agent/schemas.py)과 1:1 대응한다.
 *
 * <p>{@code sections}를 {@code Map} 리스트로 받는 이유: AI가 태그를 늘려도 Spring이 따라 바뀌지
 * 않게 하기 위해서다. 다만 목록 카드(P1)에 필요한 {@code risk_level}·{@code title}만은 서비스가
 * 읽어 컬럼으로 뽑으므로, 그 두 키는 사실상 계약이다.
 *
 * <p>{@code generatedAt}은 선택 — AI 클라이언트가 현재 이 필드를 드롭하고 있어
 * 없으면 서버 수신 시각으로 채운다.
 */
public record DailyReportIngestRequest(
        @NotNull Long careTargetId,
        @NotNull LocalDate reportDate,
        @NotEmpty List<Map<String, Object>> sections,
        Map<String, Object> metrics,
        Instant generatedAt
) {
    /** sections 원소에서 서버가 읽는 키. 이름이 바뀌면 P1 카드가 빈다. */
    public static final String SECTION_RISK_LEVEL = "risk_level";
    public static final String SECTION_TITLE = "title";
}
