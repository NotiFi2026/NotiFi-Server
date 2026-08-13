package com.notifi.server.domain.report.dto;

import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.Instant;
import java.time.LocalDate;

/**
 * P1 목록 카드. sections 전문 대신 대표 등급·제목만 싣는다 — 상세는 P2로 간다.
 *
 * <p>이 레코드는 {@code DailyReportRepository.findSummaries}의 JPQL 생성자 프로젝션이
 * 직접 채운다. <b>생성자 파라미터 순서·타입이 그 쿼리와 계약</b>이므로 함부로 바꾸면
 * 런타임에 터진다(컴파일은 통과한다).
 */
public record DailyReportSummaryResponse(
        Long dailyReportId,
        LocalDate reportDate,
        RiskLevel riskLevel,
        String headline,
        Instant generatedAt
) {}
