package com.dms.recommendation;

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
 * The supervisor's evaluation summary sheet (Annexure-6), one per dissertation.
 *
 * <p>The form is headed confidential, so this is readable by the supervisor who
 * wrote it and by the coordinator, and by no route the student can reach. The
 * viva questions are why: the guidelines leave it to the supervisor whether they
 * are put to the candidate beforehand.
 */
@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false, unique = true)
    Allocation allocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    Verdict verdict;

    /** Annexure-6 section 4(i): organisation and presentation. */
    @Column(columnDefinition = "TEXT")
    String organisation;

    /** Section 5(ii): the technical contents of the thesis. */
    @Column(name = "technical_content", columnDefinition = "TEXT")
    String technicalContent;

    /** Section 5(iii): highlights, strong and weak points. */
    @Column(columnDefinition = "TEXT")
    String strengths;

    /** Section 6: queries and suggestions for modification. */
    @Column(columnDefinition = "TEXT")
    String queries;

    /** Section 8: up to five questions for the oral examination. */
    @Column(name = "viva_questions", columnDefinition = "TEXT")
    String vivaQuestions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by", nullable = false)
    User submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    Instant submittedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
