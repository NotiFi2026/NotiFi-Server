package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SensingEventRepository extends JpaRepository<SensingEvent, Long> {

    Optional<SensingEvent> findByCareTargetIdAndDetectedAtAndEventType(
            Long careTargetId, Instant detectedAt, EventType eventType);

    // S1: 가장 최근 이벤트 1건 (위험도·last_activity_at 산출)
    Optional<SensingEvent> findFirstByCareTargetIdOrderByDetectedAtDesc(Long careTargetId);

    // S2: 필터 페이지 조회 (nullable 파라미터)
    @Query("SELECT se FROM SensingEvent se WHERE se.careTargetId = :ctId " +
           "AND (:eventType IS NULL OR se.eventType = :eventType) " +
           "AND (:from IS NULL OR se.detectedAt >= :from) " +
           "AND (:to IS NULL OR se.detectedAt <= :to)")
    Page<SensingEvent> findEvents(@Param("ctId") Long careTargetId,
                                  @Param("eventType") EventType eventType,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
