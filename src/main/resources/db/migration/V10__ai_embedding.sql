-- Phase 7: embeddings and AI advisory output.
--
-- Vectors are stored as JSONB rather than a pgvector column because the
-- extension is not installed on the target server. At department scale -- tens
-- of topics per session -- an exact cosine scan in Java is instant, and the
-- SimilarityProvider seam means swapping to pgvector later is one class plus a
-- migration that copies these arrays into a vector(768) column.
--
-- One embedding row per source record, keyed by kind + reference, so topics and
-- supervisor profiles share one table without a discriminator on either entity.

CREATE TABLE embeddings (
    id           BIGSERIAL PRIMARY KEY,
    kind         VARCHAR(32)  NOT NULL,
    ref_id       BIGINT       NOT NULL,
    model        VARCHAR(64)  NOT NULL,
    dimensions   INT          NOT NULL,
    vector       JSONB        NOT NULL,
    source_hash  VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_embeddings_kind_ref UNIQUE (kind, ref_id),
    CONSTRAINT ck_embeddings_dimensions CHECK (dimensions > 0)
);

CREATE INDEX idx_embeddings_kind ON embeddings (kind);

-- Advisory output is persisted so a report can be shown again without paying for
-- a second call, and so the record shows what the model actually said at the time.
-- ai_generated is always true here; the column exists so the flag travels with the
-- row rather than being implied by the table it sits in.
CREATE TABLE ai_reports (
    id           BIGSERIAL PRIMARY KEY,
    kind         VARCHAR(32)  NOT NULL,
    ref_id       BIGINT       NOT NULL,
    model        VARCHAR(64)  NOT NULL,
    body         TEXT         NOT NULL,
    ai_generated BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_reports_kind_ref UNIQUE (kind, ref_id)
);

CREATE INDEX idx_ai_reports_kind ON ai_reports (kind);
