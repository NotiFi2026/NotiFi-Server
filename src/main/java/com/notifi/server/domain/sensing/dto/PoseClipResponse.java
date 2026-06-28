package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.sensing.entity.PoseClip;

import java.time.Instant;
import java.util.Map;

public record PoseClipResponse(
        Long poseClipId,
        Long sensingEventId,
        String modelVersion,
        String jointSchema,
        int fps,
        int frameCount,
        Integer durationMs,
        Instant windowStartAt,
        Instant windowEndAt,
        Map<String, Object> frames,
        Map<String, Object> eventTimeline
) {
    public static PoseClipResponse from(PoseClip clip) {
        return new PoseClipResponse(
                clip.getId(),
                clip.getSensingEventId(),
                clip.getModelVersion(),
                clip.getJointSchema(),
                clip.getFps(),           // short → int (자동 확장)
                clip.getFrameCount(),
                clip.getDurationMs(),
                clip.getWindowStartAt(),
                clip.getWindowEndAt(),
                clip.getFrames(),
                clip.getEventTimeline()
        );
    }
}
