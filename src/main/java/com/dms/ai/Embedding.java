package com.dms.ai;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One stored vector, keyed by what it describes.
 *
 * <p>JSONB rather than a pgvector column because the extension is not installed
 * on the target server. At department scale an exact scan is instant; the
 * SimilarityProvider seam is what makes that an implementation detail.
 *
 * <p>sourceHash is the digest of the text that was embedded, so a topic whose
 * abstract has not changed is never re-embedded and never re-billed.
 */
@Entity
@Table(name = "embeddings")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Embedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    EmbeddingKind kind;

    /** Id of the topic or supervisor profile this describes. */
    @Column(name = "ref_id", nullable = false)
    Long refId;

    @Column(nullable = false, length = 64)
    String model;

    @Column(nullable = false)
    int dimensions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vector", nullable = false, columnDefinition = "jsonb")
    List<Double> vector = new ArrayList<>();

    @Column(name = "source_hash", nullable = false, length = 64)
    String sourceHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    /** Primitive copy for the hot loop -- boxing 768 Doubles per comparison is not free. */
    public double[] toArray() {
        double[] out = new double[vector.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = vector.get(i);
        }
        return out;
    }
}
