package com.notifi.server.domain.report.repository;

import com.notifi.server.domain.report.entity.DailyReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    // I3: (care_target_id, report_date) 멱등 UPSERT 조회
    Optional<DailyReport> findByCareTargetIdAndReportDate(Long careTargetId, LocalDate reportDate);

    // P1: 노인별 리포트 목록 (정렬은 서비스가 report_date DESC로 고정)
    Page<DailyReport> findByCareTargetId(Long careTargetId, Pageable pageable);
}
