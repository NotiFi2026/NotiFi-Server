package com.notifi.server.domain.sensing.dto;

public record PoseClipIngestResponse(
        Long poseClipId,
        Long sensingEventId
) {
}
