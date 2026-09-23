package com.dms.viva;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * When and where a student defends, and which external examiners attend.
 *
 * <p>The internal panel is not stored here. It is the student's {@code panel_members}
 * rows, read at display time, so a booking can never name faculty the coordinator
 * did not appoint. Only the external examiners are free text: they are usually not
 * users of this system, and modelling them as accounts would mean creating logins
 * for people who never sign in. The column keeps its phase-6 name, {@code panel}.
 */
@Entity
@Table(name = "viva_schedules")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VivaSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false, unique = true)
    Allocation allocation;

    @Column(name = "scheduled_at", nullable = false)
    Instant scheduledAt;

    @Column(nullable = false, length = 255)
    String venue;

    @Column(name = "panel", columnDefinition = "TEXT")
    String externalExaminers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    VivaStatus status = VivaStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheduled_by")
    User scheduledBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
