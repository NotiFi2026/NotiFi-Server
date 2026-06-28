package com.notifi.server.domain.sensing.dto;

import com.notifi.server.domain.device.entity.Device;
import com.notifi.server.domain.device.entity.DeviceStatus;

public record DeviceStatusItem(
        Long deviceId,
        String room,
        DeviceStatus status
) {
    public static DeviceStatusItem from(Device d) {
        return new DeviceStatusItem(d.getId(), d.getRoom(), d.getStatus());
    }
}
