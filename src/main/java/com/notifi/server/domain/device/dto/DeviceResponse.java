package com.notifi.server.domain.device.dto;

import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.entity.DeviceStatus;
import com.notifi.server.domain.device.entity.NodeRole;

import java.time.Instant;

public record DeviceResponse(
        Long deviceId,
        String deviceUid,
        String room,
        String positionLabel,
        NodeRole nodeRole,
        DeviceStatus status,
        String firmwareVersion,
        Instant lastSeenAt,
        Instant registeredAt
) {
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getId(),
                d.getDeviceUid(),
                d.getRoom(),
                d.getPositionLabel(),
                d.getNodeRole(),
                d.getStatus(),
                d.getFirmwareVersion(),
                d.getLastSeenAt(),
                d.getRegisteredAt()
        );
    }
}
