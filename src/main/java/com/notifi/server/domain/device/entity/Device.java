package com.notifi.server.domain.device.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Table(name = "tb_device")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @Column(name = "care_target_id", nullable = false)
    private Long careTargetId;

    @Column(name = "device_uid", nullable = false, length = 64, unique = true)
    private String deviceUid;

    @Column(length = 50)
    private String room;

    @Column(name = "position_label", length = 100)
    private String positionLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_role", length = 20)
    private NodeRole nodeRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    @Column(name = "firmware_version", length = 30)
    private String firmwareVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    public static Device create(Long careTargetId, String deviceUid, String room,
                                String positionLabel, NodeRole nodeRole, String firmwareVersion) {
        Device d = new Device();
        d.careTargetId = careTargetId;
        d.deviceUid = deviceUid;
        d.room = room;
        d.positionLabel = positionLabel;
        d.nodeRole = nodeRole;
        d.firmwareVersion = firmwareVersion;
        d.status = DeviceStatus.ACTIVE;
        return d;
    }

    public void recordHeartbeat(Instant at) {
        this.lastSeenAt = at;
    }

    public void update(String room, String positionLabel, NodeRole nodeRole, DeviceStatus status) {
        if (room != null) this.room = room;
        if (positionLabel != null) this.positionLabel = positionLabel;
        if (nodeRole != null) this.nodeRole = nodeRole;
        if (status != null) this.status = status;
    }
}
