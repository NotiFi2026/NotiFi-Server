package com.notifi.server.domain.caretarget.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tb_care_relationship")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relationship_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_target_id", nullable = false)
    private CareTarget careTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private RelationshipType relationshipType;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "notify_priority", nullable = false)
    private short notifyPriority;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CareRelationship of(Long userId, CareTarget careTarget,
                                      RelationshipType type, boolean isPrimary, short notifyPriority) {
        CareRelationship cr = new CareRelationship();
        cr.userId = userId;
        cr.careTarget = careTarget;
        cr.relationshipType = type;
        cr.isPrimary = isPrimary;
        cr.notifyPriority = notifyPriority;
        return cr;
    }
}
