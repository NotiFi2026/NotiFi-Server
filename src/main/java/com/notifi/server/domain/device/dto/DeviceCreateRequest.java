package com.notifi.server.domain.device.dto;

import com.notifi.server.domain.device.entity.NodeRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceCreateRequest(
        @NotBlank @Size(max = 64) String deviceUid,
        @Size(max = 50) String room,
        @Size(max = 100) String positionLabel,
        NodeRole nodeRole,
        @Size(max = 30) String firmwareVersion
) {}
