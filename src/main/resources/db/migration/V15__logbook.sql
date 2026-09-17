-- Phase 13: the dissertation logbook (guidelines Annexure-4, section 2.2.3).
--
-- One row per meeting between student and guide: what was assigned, what was
-- done, what the guide said, and the guide's signature. The student writes the
-- row; the guide signs it or returns it. Once signed the row is frozen by the
-- state machine and its digest is stored, and the certificate lists every
-- signed digest -- so a signed meeting that is later edited breaks verification
-- the same way an edited mark does.

CREATE TABLE logbook_entries (
    id                 BIGSERIAL PRIMARY KEY,
    allocation_id      BIGINT       NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    meeting_no         INT          NOT NULL,
    meeting_at         TIMESTAMPTZ  NOT NULL,
    work_assigned      TEXT         NOT NULL,
    work_completed     TEXT         NOT NULL,
    challenges         TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    supervisor_remarks TEXT,
    signed_by          BIGINT                REFERENCES users (id) ON DELETE SET NULL,
    signed_at          TIMESTAMPTZ,
    -- SHA-256 over the row's content at the moment of signing. Null until then.
    entry_digest       VARCHAR(64),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_logbook_allocation_meeting UNIQUE (allocation_id, meeting_no),
    CONSTRAINT ck_logbook_meeting_no CHECK (meeting_no > 0),
    -- A signed row carries a signature and a digest; an unsigned one carries neither.
    CONSTRAINT ck_logbook_signed_consistent CHECK (
        (status = 'SIGNED' AND signed_at IS NOT NULL AND entry_digest IS NOT NULL)
        OR (status <> 'SIGNED' AND signed_at IS NULL AND entry_digest IS NULL)
    )
);

CREATE INDEX idx_logbook_allocation ON logbook_entries (allocation_id);
CREATE INDEX idx_logbook_status     ON logbook_entries (status);
