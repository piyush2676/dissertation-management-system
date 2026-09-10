package com.dms.evaluation;

import com.dms.session.AcademicSession;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * One line of the marking scheme, scoped to a session.
 *
 * <p>Rows rather than an enum: a department that weights things differently next
 * year changes data, not code. Evaluation.scores keys off this id, so adding a
 * criterion needs no migration either.
 */
@Entity
@Table(name = "rubric_criteria")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RubricCriterion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    AcademicSession session;

    @Column(nullable = false, length = 128)
    String name;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "max_marks", nullable = false)
    int maxMarks;

    @Column(nullable = false)
    int weightage;

    @Column(name = "sequence_no", nullable = false)
    int sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
