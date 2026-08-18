
CREATE TABLE academic_sessions (
    id         BIGSERIAL PRIMARY KEY,
    label      VARCHAR(32)  NOT NULL,
    programme  VARCHAR(32)  NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sessions_label_programme UNIQUE (label, programme),
    CONSTRAINT ck_sessions_dates CHECK (end_date > start_date)
);

CREATE UNIQUE INDEX uq_sessions_one_active_per_programme
    ON academic_sessions (programme)
    WHERE active;

CREATE TABLE milestones (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT       NOT NULL REFERENCES academic_sessions (id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    due_date    DATE         NOT NULL,
    weightage   INT          NOT NULL DEFAULT 0,
    sequence_no INT          NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_milestones_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT ck_milestones_weightage CHECK (weightage BETWEEN 0 AND 100)
);

CREATE TABLE allocations (
    id              BIGSERIAL PRIMARY KEY,
    student_id      BIGINT      NOT NULL REFERENCES student_profiles (id)    ON DELETE CASCADE,
    supervisor_id   BIGINT      NOT NULL REFERENCES supervisor_profiles (id) ON DELETE RESTRICT,
    session_id      BIGINT      NOT NULL REFERENCES academic_sessions (id)   ON DELETE CASCADE,
    topic_id        BIGINT               REFERENCES topics (id)              ON DELETE SET NULL,
    status          VARCHAR(32) NOT NULL,
    decision_reason TEXT,
    allocated_by    BIGINT               REFERENCES users (id)               ON DELETE SET NULL,
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_allocations_one_live_per_student_session
    ON allocations (student_id, session_id)
    WHERE status IN ('REQUESTED', 'ACCEPTED', 'COORDINATOR_ASSIGNED');

CREATE INDEX idx_allocations_supervisor_status ON allocations (supervisor_id, status);
CREATE INDEX idx_allocations_session           ON allocations (session_id);
CREATE INDEX idx_allocations_student           ON allocations (student_id);
CREATE INDEX idx_milestones_session            ON milestones (session_id);
