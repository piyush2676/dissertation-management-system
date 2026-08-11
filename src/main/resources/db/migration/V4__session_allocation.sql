-- =========================================================================
-- V4 -- academic sessions, milestones, guide allocation
--
-- Flyway applies this once and records a checksum in flyway_schema_history.
-- DO NOT EDIT after it has run: any change makes the checksum mismatch and
-- every later startup fails. Corrections go in V5__*.sql.
--
-- Three tables, one idea each:
--   academic_sessions  the year a cohort runs in
--   milestones         the workflow itself, stored as rows
--   allocations        which guide supervises which student, and how that
--                      was decided
-- =========================================================================

-- One row per (programme, year). B.Tech 2025-26 and M.Tech 2025-26 are two
-- separate rows because their timelines differ. That separation is what keeps
-- "if programme == BTECH" out of the service layer entirely.
CREATE TABLE academic_sessions (
    id         BIGSERIAL PRIMARY KEY,
    label      VARCHAR(32)  NOT NULL,          -- "2025-26"
    programme  VARCHAR(32)  NOT NULL,          -- BTECH | MTECH | BTECH_MTECH_INTEGRATED
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sessions_label_programme UNIQUE (label, programme),
    CONSTRAINT ck_sessions_dates CHECK (end_date > start_date)
);

-- At most one active session per programme.
--
-- A partial unique index, not a UNIQUE constraint: UNIQUE (programme, active)
-- would also allow only one INACTIVE session per programme, so last year's row
-- and the year before would collide. The WHERE clause scopes the rule to live
-- rows only.
--
-- Enforced by the database rather than by a service check because "which
-- session is current" is read on nearly every request, and a second active row
-- splits the cohort in two without anything failing -- a bug nobody notices
-- until the marks are wrong.
CREATE UNIQUE INDEX uq_sessions_one_active_per_programme
    ON academic_sessions (programme)
    WHERE active;

-- The workflow, as data.
--
-- Adding a "Pre-submission Seminar" between two existing stages is an INSERT
-- plus a sequence_no renumber: no recompile, no migration. This is the table
-- that lets the process change without a rewrite, and it is the only place
-- B.Tech and M.Tech actually differ.
CREATE TABLE milestones (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES academic_sessions (id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,         -- "Interim Report"
    description TEXT,                          -- brief shown to the student
    due_date    DATE         NOT NULL,
    weightage   INT          NOT NULL DEFAULT 0,
    sequence_no INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_milestones_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT ck_milestones_weightage CHECK (weightage BETWEEN 0 AND 100)
);

-- Who supervises whom.
--
-- supervisor_id is ON DELETE RESTRICT, alone among these references. An
-- allocation with no supervisor is meaningless, so deleting a professor who
-- still supervises someone must fail loudly rather than leave a student
-- pointing at nobody. Retiring a guide is users.enabled = FALSE, not a DELETE.
--
-- topic_id is SET NULL: the allocation outlives a topic that gets superseded.
-- allocated_by is SET NULL for the same reason decided_by is in V3 -- removing
-- a retired coordinator's account must not delete the allocations they made.
CREATE TABLE allocations (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT      NOT NULL REFERENCES student_profiles (id)    ON DELETE CASCADE,
    supervisor_id   BIGINT      NOT NULL REFERENCES supervisor_profiles (id) ON DELETE RESTRICT,
    session_id      BIGINT      NOT NULL REFERENCES academic_sessions (id)   ON DELETE CASCADE,
    topic_id        BIGINT               REFERENCES topics (id)              ON DELETE SET NULL,
    status          VARCHAR(32) NOT NULL,
    decision_reason TEXT,                      -- why declined; null on an accept
    allocated_by    BIGINT               REFERENCES users (id)               ON DELETE SET NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ                -- null until the guide answers
);

-- One live allocation per student per session.
--
-- Partial again, and this is the subtle one. A plain
-- UNIQUE (student_id, session_id) looks right and is wrong: a student whose
-- first request is DECLINED must be able to ask a second guide, and the plain
-- constraint blocks that retry forever. Scoping the rule to the live statuses
-- lets declined and withdrawn rows accumulate as history while exactly one
-- live row stays possible.
--
-- This status list is the same set as AllocationStatus.LIVE. If a status is
-- ever added, both move together.
CREATE UNIQUE INDEX uq_allocations_one_live_per_student_session
    ON allocations (student_id, session_id)
    WHERE status IN ('REQUESTED', 'ACCEPTED', 'COORDINATOR_ASSIGNED');

-- (supervisor_id, status) is the capacity count's index: it runs on every
-- request and again on every accept.
CREATE INDEX idx_allocations_supervisor_status ON allocations (supervisor_id, status);
CREATE INDEX idx_allocations_session           ON allocations (session_id);
CREATE INDEX idx_allocations_student           ON allocations (student_id);
CREATE INDEX idx_milestones_session            ON milestones (session_id);
