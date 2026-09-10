package com.dms.evaluation;

import com.dms.allocation.Allocation;
import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * One examiner's marks for one student.
 *
 * <p>scores is JSONB keyed by rubric criterion id rather than a column per
 * criterion, which is what lets the department add a criterion without a
 * migration. The weighted total is stored alongside so a mark sheet does not have
 * to recompute history every time the rubric changes.
 */
@Entity
@Table(name = "evaluations")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", nullable = false)
    Allocation allocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examiner_id", nullable = false)
    User examiner;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    Map<String, Integer> scores = new HashMap<>();

    @Column(nullable = false, precision = 6, scale = 2)
    BigDecimal total = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    String remarks;

    @Column(name = "submitted_at", nullable = false)
    Instant submittedAt = Instant.now();
}
