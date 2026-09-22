-- Phase 15: the review panel (guidelines section 2.2.1) and the supervisor's
-- evaluation summary sheet (Annexure-6).
--
-- A panel is per student: the faculty who assess that student's review
-- presentations. Their marks are ordinary evaluation rows -- the table has been
-- keyed by (allocation, examiner) since phase 6 -- so nothing about scoring,
-- averaging or the pass line changes. What is new is who may write one.

CREATE TABLE panel_members (
    id            BIGSERIAL PRIMARY KEY,
    allocation_id BIGINT      NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    member_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    added_by      BIGINT               REFERENCES users (id) ON DELETE SET NULL,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_panel_allocation_member UNIQUE (allocation_id, member_id)
);

CREATE INDEX idx_panel_allocation ON panel_members (allocation_id);
CREATE INDEX idx_panel_member     ON panel_members (member_id);

-- The student's own guide and co-supervisor are refused in the service: the
-- check needs a join, which a CHECK constraint cannot do, and the rule is about
-- what an average means rather than about row shape.

-- ---- Annexure-6: the supervisor's summary sheet -----------------------------

-- One per dissertation, written by the supervisor, confidential to them and the
-- coordinator. The verdict is the guidelines' [A] to [D].
CREATE TABLE recommendations (
    id                BIGSERIAL PRIMARY KEY,
    allocation_id     BIGINT      NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    verdict           VARCHAR(24) NOT NULL,
    organisation      TEXT,
    technical_content TEXT,
    strengths         TEXT,
    queries           TEXT,
    viva_questions    TEXT,
    submitted_by      BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    submitted_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_recommendation_allocation UNIQUE (allocation_id)
);

CREATE INDEX idx_recommendations_allocation ON recommendations (allocation_id);
