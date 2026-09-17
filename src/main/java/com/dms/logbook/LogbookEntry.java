package com.dms.logbook;

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
 * One meeting between a student and their guide, as Annexure-4 records it.
 *
 * <p>The student writes the row after the meeting; the guide signs it or returns
 * it. A signed row is frozen by {@link LogbookEntryStatus} and carries the digest
 * of its own content, which the certificate lists. Nothing here is ever deleted
 * from the UI -- a wrong entry is returned and corrected, and the correction is
 * what gets signed.
 */
@Entity
@Table(name = "logbook_entries")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogbookEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    /** 1, 2, 3 ... per allocation, in the order the student recorded them. */
    @Column(name = "meeting_no", nullable = false)
    int meetingNo;

    @Column(name = "meeting_at", nullable = false)
    Instant meetingAt;

    @Column(name = "work_assigned", nullable = false, columnDefinition = "TEXT")
    String workAssigned;

    @Column(name = "work_completed", nullable = false, columnDefinition = "TEXT")
    String workCompleted;

    @Column(columnDefinition = "TEXT")
    String challenges;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    LogbookEntryStatus status = LogbookEntryStatus.PENDING;

    @Column(name = "supervisor_remarks", columnDefinition = "TEXT")
    String supervisorRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_by")
    User signedBy;

    @Column(name = "signed_at")
    Instant signedAt;

    /** SHA-256 over the row at signing. Null until signed. */
    @Column(name = "entry_digest", length = 64)
    String entryDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
