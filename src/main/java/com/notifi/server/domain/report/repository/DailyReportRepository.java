package com.notifi.server.domain.report.repository;

import com.notifi.server.domain.report.dto.DailyReportSummaryResponse;
import com.notifi.server.domain.report.entity.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    // I3: (care_target_id, report_date) 멱등 UPSERT 조회
    Optional<DailyReport> findByCareTargetIdAndReportDate(Long careTargetId, LocalDate reportDate);

    /**
     * P1: 노인별 리포트 목록 (정렬은 컨트롤러가 report_date DESC로 고정).
     *
     * <p>엔티티가 아니라 생성자 프로젝션으로 받는 이유: {@code sections}·{@code metrics}는
     * JSONB 기본 속성이라 엔티티를 로드하면 <b>즉시 함께 페치된다.</b> 목록은 그 둘을 쓰지 않으므로
     * 페이지마다 수십 KB를 읽어 역직렬화한 뒤 버리게 된다. 카드용 값을 컬럼으로 비정규화해 둔
     * 이유 자체가 목록에서 JSONB를 건드리지 않기 위함이므로, 조회도 그 컬럼만 읽어야 말이 맞는다.
     */
    @Query("SELECT new com.notifi.server.domain.report.dto.DailyReportSummaryResponse("
         + "r.id, r.reportDate, r.topRiskLevel, r.headline, r.generatedAt) "
         + "FROM DailyReport r WHERE r.careTargetId = :ctId")
    Page<DailyReportSummaryResponse> findSummaries(@Param("ctId") Long careTargetId, Pageable pageable);
}
