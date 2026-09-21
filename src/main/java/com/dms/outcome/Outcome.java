package com.dms.outcome;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One tangible result of the dissertation: a paper, a patent, a product. The
 * student reports it; the coordinator verifies it against the evidence. Any edit
 * after verification clears the verification, so a verified row always describes
 * what the coordinator actually saw.
 */
@Entity
@Table(name = "outcomes")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Outcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    OutcomeKind kind;

    @Column(nullable = false)
    String title;

    @Column(length = 255)
    String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    OutcomeIndexing indexing = OutcomeIndexing.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    OutcomeStatus status;

    /** DOI, application number, URL -- whatever lets the coordinator find it. */
    @Column(length = 255)
    String reference;

    @Column(name = "outcome_date")
    LocalDate outcomeDate;

    @Column(columnDefinition = "TEXT")
    String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    User verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    String verificationNote;

    public boolean isVerified() {
        return verifiedAt != null;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
