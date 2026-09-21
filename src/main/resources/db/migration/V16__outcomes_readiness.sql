-- Phase 14: outcomes, plagiarism checks, and which document a review slot collects.
--
-- The guidelines make the dissertation accountable for tangible results (§4.4:
-- papers, patents, prototypes), for originality (§8.3: similarity under 10%,
-- AI-generated content 0%), and for a fixed set of documents (§2.2.3). This
-- migration gives each of those a home. The readiness ledger that reads them is
-- application code; nothing here enforces a gate.

-- ---- outcomes: what the work produced -----------------------------------------

CREATE TABLE outcomes (
    id                BIGSERIAL PRIMARY KEY,
    allocation_id     BIGINT       NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    kind              VARCHAR(24)  NOT NULL,      -- JOURNAL_PAPER, CONFERENCE_PAPER, PATENT, PRODUCT, OTHER
    title             VARCHAR(255) NOT NULL,
    venue             VARCHAR(255),               -- journal, conference, patent office, repository
    indexing          VARCHAR(16)  NOT NULL DEFAULT 'NONE',  -- SCI, SCOPUS, IEEE, ESCI, OTHER, NONE
    status            VARCHAR(16)  NOT NULL,      -- DRAFTING, COMMUNICATED, ACCEPTED, PUBLISHED, FILED, GRANTED
    reference         VARCHAR(255),               -- DOI, application number, URL
    outcome_date      DATE,
    notes             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- The coordinator's verification. Cleared by any later edit, so a verified row
    -- always describes what was actually seen.
    verified_by       BIGINT                REFERENCES users (id) ON DELETE SET NULL,
    verified_at       TIMESTAMPTZ,
    verification_note TEXT
);

CREATE INDEX idx_outcomes_allocation ON outcomes (allocation_id);
CREATE INDEX idx_outcomes_unverified ON outcomes (verified_at) WHERE verified_at IS NULL;

-- ---- plagiarism checks: one per version, never on the version --------------

-- submission_versions is append-only by policy and the provenance design leans
-- on that, so the check is its own row rather than three columns on the version.
CREATE TABLE plagiarism_checks (
    id                 BIGSERIAL PRIMARY KEY,
    version_id         BIGINT        NOT NULL REFERENCES submission_versions (id) ON DELETE CASCADE,
    similarity_percent NUMERIC(5,2)  NOT NULL,
    ai_percent         NUMERIC(5,2)  NOT NULL,
    tool               VARCHAR(64),               -- Turnitin, iThenticate, Urkund ...
    note               VARCHAR(255),
    checked_by         BIGINT        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    checked_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plagiarism_version UNIQUE (version_id),
    CONSTRAINT ck_plagiarism_similarity CHECK (similarity_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_plagiarism_ai CHECK (ai_percent BETWEEN 0 AND 100)
);

-- ---- milestones: which of the seven documents this slot collects ------------

ALTER TABLE milestones ADD COLUMN deliverable VARCHAR(32);

-- Backfill by name. The phase-12 PRE track and the legacy FINAL track both have
-- recognisable names; anything else stays null and simply does not count toward
-- the checklist.
UPDATE milestones SET deliverable = 'SYNOPSIS'          WHERE deliverable IS NULL AND (name ILIKE 'Synopsis%' OR name ILIKE 'Review 1 - Problem%');
UPDATE milestones SET deliverable = 'LITERATURE_SURVEY' WHERE deliverable IS NULL AND (name ILIKE 'Literature%' OR name ILIKE 'Review 2 - Synopsis%');
UPDATE milestones SET deliverable = 'SYSTEM_DESIGN'     WHERE deliverable IS NULL AND (name ILIKE 'Interim%' OR name ILIKE 'Review 3 - Implementation%');
UPDATE milestones SET deliverable = 'TECHNICAL_REPORT'  WHERE deliverable IS NULL AND (name ILIKE 'Pre-submission%' OR name ILIKE 'Review 2 - Results%');
UPDATE milestones SET deliverable = 'FINAL_THESIS'      WHERE deliverable IS NULL AND (name ILIKE 'Final%' OR name ILIKE 'Review 3 - Final%');
