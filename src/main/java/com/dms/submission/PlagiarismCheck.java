package com.dms.submission;

import com.dms.user.User;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The similarity and AI-generated percentages the guide read off the report for
 * one version (guidelines section 8.3: under 10% and 0%). Its own row, one per
 * version, because SubmissionVersion is append-only and stays that way.
 */
@Entity
@Table(name = "plagiarism_checks")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlagiarismCheck {

    /** Section 8.3: acceptable similarity is strictly under this. */
    public static final BigDecimal MAX_SIMILARITY_PERCENT = BigDecimal.TEN;
    /** Section 8.3: AI-generated content must be exactly this. */
    public static final BigDecimal MAX_AI_PERCENT = BigDecimal.ZERO;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false, unique = true)
    SubmissionVersion version;

    @Column(name = "similarity_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal similarityPercent;

    @Column(name = "ai_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal aiPercent;

    @Column(length = 64)
    String tool;

    @Column(length = 255)
    String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by", nullable = false)
    User checkedBy;

    @Column(name = "checked_at", nullable = false)
    Instant checkedAt = Instant.now();

    /** Within both thresholds. */
    public boolean passes() {
        return similarityPercent.compareTo(MAX_SIMILARITY_PERCENT) < 0
                && aiPercent.compareTo(MAX_AI_PERCENT) <= 0;
    }
}
