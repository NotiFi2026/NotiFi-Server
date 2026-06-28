package com.notifi.server.domain.sensing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public record PoseClipIngestRequest(

        @NotNull
        @Size(max = 30)
        String modelVersion,

        @NotNull
        @Size(max = 30)
        String jointSchema,

        @NotNull
        @Min(1)
        @Max(32767)   // SMALLINT 상한 — 초과 시 shortValue() 절단 방지
        Integer fps,

        @NotNull
        @Min(0)
        Integer frameCount,

        Integer durationMs,

        @NotNull
        Instant windowStartAt,

        @NotNull
        Instant windowEndAt,

        @NotNull
        Map<String, Object> frames,

        Map<String, Object> eventTimeline
) {
}
