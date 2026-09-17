package com.dms.evaluation;

import com.dms.session.AcademicSession;
import com.dms.session.DissertationPhase;

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

    /** Pre and Final Dissertation are marked on different schemes (Format 6 vs Format 15). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    DissertationPhase phase;

    @Column(nullable = false, length = 128)
    String name;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "max_marks", nullable = false)
    int maxMarks;

    /**
     * Seeded equal to maxMarks since phase 12, so the weighted total is the plain sum
     * of marks and reads against the phase maximum (100 for PRE, 200 for FINAL).
     */
    @Column(nullable = false)
    int weightage;

    /** Course outcome this row evidences, as printed in the guidelines ("CO2"). */
    @Column(name = "co_code", length = 8)
    String coCode;

    /** Programme outcomes this row maps to, comma separated ("PO1,PO2,PO4"). */
    @Column(name = "po_mapping", length = 64)
    String poMapping;

    @Column(name = "sequence_no", nullable = false)
    int sequenceNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
