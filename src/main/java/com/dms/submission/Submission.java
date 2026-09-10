package com.dms.submission;

import com.dms.allocation.Allocation;
import com.dms.session.Milestone;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * The logical slot: one milestone for one allocation. Each physical upload is a
 * SubmissionVersion hanging off this row, so the guide always reads the latest
 * while the history stays intact.
 */
@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", nullable = false)
    Milestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    SubmissionStatus status = SubmissionStatus.DRAFT;

    /** Mirrors the highest version_no on record. Zero until the first upload. */
    @Column(name = "current_version_no", nullable = false)
    int currentVersionNo;

    /** Set when the first version arrives after the milestone due date. */
    @Column(name = "late", nullable = false)
    boolean late;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    User decidedBy;

    @Column(name = "decided_at")
    Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
