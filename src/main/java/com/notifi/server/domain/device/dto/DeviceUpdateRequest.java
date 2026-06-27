package com.notifi.server.domain.device.dto;

import com.notifi.server.domain.device.entity.DeviceStatus;
import com.notifi.server.domain.device.entity.NodeRole;
import jakarta.validation.constraints.Size;

public record DeviceUpdateRequest(
        @Size(max = 50) String room,
        @Size(max = 100) String positionLabel,
        NodeRole nodeRole,
        DeviceStatus status
) {}
