package com.dms.audit;

import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/** One immutable line in the trail: who did what, to which record, and when. */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    User actor;

    /** Kept alongside the FK so the trail survives the user record being removed. */
    @Column(name = "actor_email", nullable = false, length = 255)
    String actorEmail;

    @Column(nullable = false, length = 64)
    String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    String entityType;

    @Column(name = "entity_id", nullable = false)
    Long entityId;

    @Column(name = "old_value", length = 255)
    String oldValue;

    @Column(name = "new_value", length = 255)
    String newValue;

    @Column(nullable = false, updatable = false)
    Instant at = Instant.now();
}
