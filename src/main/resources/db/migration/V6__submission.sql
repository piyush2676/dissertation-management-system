-- Phase 4: milestone submissions.
--
-- submissions is the logical slot ("Interim Report for Ravi"); submission_versions
-- is each physical upload against that slot. The split is what lets the guide always
-- see the latest file while every earlier one stays on record. Versions are
-- append-only -- nothing in the application updates or deletes a row here.

CREATE TABLE submissions (
    id                 BIGSERIAL PRIMARY KEY,
    allocation_id      BIGINT      NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    milestone_id       BIGINT      NOT NULL REFERENCES milestones (id) ON DELETE RESTRICT,
    status             VARCHAR(32) NOT NULL,
    current_version_no INT         NOT NULL DEFAULT 0,
    late               BOOLEAN     NOT NULL DEFAULT FALSE,
    decision_note      TEXT,
    decided_by         BIGINT               REFERENCES users (id) ON DELETE SET NULL,
    decided_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_submissions_allocation_milestone UNIQUE (allocation_id, milestone_id),
    CONSTRAINT ck_submissions_version_no CHECK (current_version_no >= 0)
);

CREATE TABLE submission_versions (
    id                BIGSERIAL PRIMARY KEY,
    submission_id     BIGINT       NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
    version_no        INT          NOT NULL,
    storage_path      VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(128) NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    note              TEXT,
    submitted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_submission_versions_no UNIQUE (submission_id, version_no),
    CONSTRAINT ck_submission_versions_no CHECK (version_no > 0),
    CONSTRAINT ck_submission_versions_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_submissions_allocation ON submissions (allocation_id);
CREATE INDEX idx_submissions_status     ON submissions (status);
CREATE INDEX idx_submission_versions_submission ON submission_versions (submission_id);
