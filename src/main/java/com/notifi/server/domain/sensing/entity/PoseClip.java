package com.notifi.server.domain.sensing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "tb_pose_clip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoseClip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pose_clip_id")
    private Long id;

    @Column(name = "sensing_event_id", nullable = false, unique = true)
    private Long sensingEventId;

    @Column(name = "model_version", nullable = false, length = 30)
    private String modelVersion;

    @Column(name = "joint_schema", nullable = false, length = 30)
    private String jointSchema;

    @Column(name = "fps", nullable = false)
    private short fps;

    @Column(name = "frame_count", nullable = false)
    private int frameCount;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "window_start_at", nullable = false)
    private Instant windowStartAt;

    @Column(name = "window_end_at", nullable = false)
    private Instant windowEndAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "frames", columnDefinition = "JSONB", nullable = false)
    private Map<String, Object> frames;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_timeline", columnDefinition = "JSONB")
    private Map<String, Object> eventTimeline;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PoseClip of(Long sensingEventId, String modelVersion, String jointSchema,
                              short fps, int frameCount, Integer durationMs,
                              Instant windowStartAt, Instant windowEndAt,
                              Map<String, Object> frames, Map<String, Object> eventTimeline) {
        PoseClip clip = new PoseClip();
        clip.sensingEventId = sensingEventId;
        clip.modelVersion = modelVersion;
        clip.jointSchema = jointSchema;
        clip.fps = fps;
        clip.frameCount = frameCount;
        clip.durationMs = durationMs;
        clip.windowStartAt = windowStartAt;
        clip.windowEndAt = windowEndAt;
        clip.frames = frames;
        clip.eventTimeline = eventTimeline;
        return clip;
    }
}
