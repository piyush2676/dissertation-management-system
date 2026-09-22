package com.dms.change;

import com.dms.allocation.Allocation;
import com.dms.user.SupervisorProfile;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * A scholar's written request to change supervisor or title (section 4.11).
 *
 * <p>Deliberately not a status on the allocation: the guidelines say the scholar
 * keeps working while the committee considers it, so nothing about their
 * supervision changes until a decision lands.
 */
@Entity
@Table(name = "change_requests")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ChangeKind kind;

    @Column(nullable = false, columnDefinition = "TEXT")
    String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_supervisor_id")
    SupervisorProfile preferredSupervisor;

    @Column(name = "proposed_title", length = 255)
    String proposedTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ChangeRequestStatus status = ChangeRequestStatus.PENDING;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    User requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    Instant requestedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    User decidedBy;

    @Column(name = "decided_at")
    Instant decidedAt;
}
