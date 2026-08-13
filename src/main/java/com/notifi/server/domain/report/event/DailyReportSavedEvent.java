package com.notifi.server.domain.report.event;

import com.notifi.server.domain.sensing.entity.RiskLevel;

import java.time.LocalDate;

/**
 * 신규 일일 리포트 적재 시 발행 — 커밋 이후 보호자 FCM 발송을 트리거한다.
 * 트랜잭션 안에서 직접 발송하지 않으므로 롤백 시 유령 푸시가 없다.
 *
 * <p>재적재(UPSERT 갱신)에서는 발행하지 않는다 — AI가 재시도할 때마다 보호자 폰이 울리면 안 된다.
 */
public record DailyReportSavedEvent(
        Long dailyReportId, Long careTargetId, LocalDate reportDate,
        RiskLevel topRiskLevel, String headline) {
}
