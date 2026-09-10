-- Phase 6: rubric, evaluation and viva.
--
-- The rubric is rows scoped to a session, not an enum, so a department that
-- weights things differently next year changes data rather than code. Scores are
-- JSONB keyed by criterion id, so adding a criterion needs no migration.

CREATE TABLE rubric_criteria (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES academic_sessions (id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    max_marks   INT          NOT NULL,
    weightage   INT          NOT NULL,
    sequence_no INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rubric_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT ck_rubric_max_marks CHECK (max_marks > 0),
    CONSTRAINT ck_rubric_weightage CHECK (weightage BETWEEN 0 AND 100)
);

CREATE TABLE viva_schedules (
    id            BIGSERIAL PRIMARY KEY,
    allocation_id BIGINT      NOT NULL UNIQUE REFERENCES allocations (id) ON DELETE CASCADE,
    scheduled_at  TIMESTAMPTZ NOT NULL,
    venue         VARCHAR(255) NOT NULL,
    panel         TEXT,
    status        VARCHAR(32) NOT NULL,
    scheduled_by  BIGINT               REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One evaluation per examiner per student. scores is JSONB keyed by criterion id.
CREATE TABLE evaluations (
    id            BIGSERIAL PRIMARY KEY,
    allocation_id BIGINT      NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    examiner_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    scores        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    total         NUMERIC(6,2) NOT NULL DEFAULT 0,
    remarks       TEXT,
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_evaluations_allocation_examiner UNIQUE (allocation_id, examiner_id),
    CONSTRAINT ck_evaluations_total CHECK (total >= 0)
);

CREATE INDEX idx_rubric_session       ON rubric_criteria (session_id);
CREATE INDEX idx_evaluations_alloc    ON evaluations (allocation_id);
CREATE INDEX idx_viva_scheduled_at    ON viva_schedules (scheduled_at);
