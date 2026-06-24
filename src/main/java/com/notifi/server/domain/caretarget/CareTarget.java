package com.notifi.server.domain.caretarget;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tb_care_target")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "care_target_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(length = 255)
    private String address;

    @Column(name = "emergency_memo", columnDefinition = "TEXT")
    private String emergencyMemo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static CareTarget create(String name, LocalDate birthDate, Gender gender,
                                    String address, String emergencyMemo) {
        CareTarget ct = new CareTarget();
        ct.name = name;
        ct.birthDate = birthDate;
        ct.gender = gender;
        ct.address = address;
        ct.emergencyMemo = emergencyMemo;
        return ct;
    }

    /** null 인 필드는 미변경. */
    public void update(String name, LocalDate birthDate, Gender gender,
                       String address, String emergencyMemo) {
        if (name != null) this.name = name;
        if (birthDate != null) this.birthDate = birthDate;
        if (gender != null) this.gender = gender;
        if (address != null) this.address = address;
        if (emergencyMemo != null) this.emergencyMemo = emergencyMemo;
    }
}
