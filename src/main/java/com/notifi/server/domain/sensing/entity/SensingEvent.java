package com.notifi.server.domain.sensing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tb_sensing_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sensing_event_id")
    private Long id;

    @Column(name = "care_target_id", nullable = false)
    private Long careTargetId;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "risk_probability", precision = 4, scale = 3)
    private BigDecimal riskProbability;

    @Column(name = "anomaly_score", precision = 4, scale = 3)
    private BigDecimal anomalyScore;

    @Column(name = "trend_score", precision = 4, scale = 3)
    private BigDecimal trendScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_status", length = 20)
    private SensorStatus sensorStatus;

    @Column(name = "model_version", nullable = false, length = 30)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private Map<String, Object> features;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SensingEvent create(Long careTargetId, Long deviceId, EventType eventType,
                                      BigDecimal riskProbability, BigDecimal anomalyScore,
                                      BigDecimal trendScore, SensorStatus sensorStatus,
                                      String modelVersion, Map<String, Object> features,
                                      Instant detectedAt) {
        SensingEvent e = new SensingEvent();
        e.careTargetId = careTargetId;
        e.deviceId = deviceId;
        e.eventType = eventType;
        e.riskProbability = riskProbability;
        e.anomalyScore = anomalyScore;
        e.trendScore = trendScore;
        e.sensorStatus = sensorStatus;
        e.modelVersion = modelVersion;
        e.features = features;
        e.detectedAt = detectedAt;
        return e;
    }
}
