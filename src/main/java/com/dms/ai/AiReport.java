package com.dms.ai;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Advisory text produced by a model, persisted so it can be shown again without
 * paying for a second call and so the record shows what was actually said.
 *
 * <p>aiGenerated is a stored column rather than an implication of the table, so
 * the flag travels with the row wherever it is copied.
 */
@Entity
@Table(name = "ai_reports")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    ReportKind kind;

    @Column(name = "ref_id", nullable = false)
    Long refId;

    @Column(nullable = false, length = 64)
    String model;

    @Column(nullable = false, columnDefinition = "TEXT")
    String body;

    @Column(name = "ai_generated", nullable = false)
    boolean aiGenerated = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
