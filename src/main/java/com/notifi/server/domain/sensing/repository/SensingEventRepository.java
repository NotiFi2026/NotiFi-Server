package com.notifi.server.domain.sensing.repository;

import com.notifi.server.domain.sensing.entity.EventType;
import com.notifi.server.domain.sensing.entity.SensingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface SensingEventRepository extends JpaRepository<SensingEvent, Long> {
    Optional<SensingEvent> findByCareTargetIdAndDetectedAtAndEventType(
            Long careTargetId, Instant detectedAt, EventType eventType);
}
