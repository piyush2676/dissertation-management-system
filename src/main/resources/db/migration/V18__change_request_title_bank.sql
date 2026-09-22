-- Phase 16: the formal route to change a supervisor or a thesis title
-- (guidelines section 4.11), and the faculty title bank (4.3 and 4.6).

-- ---- change requests --------------------------------------------------------
--
-- Its own row rather than a status on the allocation: section 4.11 is explicit
-- that the scholar keeps working while the committee considers the request, so
-- the allocation must stay exactly as it is until a decision lands. Approving one
-- is the only thing in the system allowed to withdraw a live allocation or send
-- an approved topic back for revision.

CREATE TABLE change_requests (
    id                      BIGSERIAL PRIMARY KEY,
    allocation_id           BIGINT      NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    kind                    VARCHAR(16) NOT NULL,          -- SUPERVISOR | TITLE
    reason                  TEXT        NOT NULL,
    -- SUPERVISOR: who the scholar would rather work under, if they have a view.
    preferred_supervisor_id BIGINT               REFERENCES supervisor_profiles (id) ON DELETE SET NULL,
    -- TITLE: the title they intend to propose instead. Indicative; the normal
    -- approval still runs afterwards.
    proposed_title          VARCHAR(255),
    status                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decision_note           TEXT,
    requested_by            BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by              BIGINT               REFERENCES users (id) ON DELETE SET NULL,
    decided_at              TIMESTAMPTZ,
    CONSTRAINT ck_change_requests_decided CHECK (
        (status = 'PENDING' AND decided_at IS NULL)
        OR (status <> 'PENDING' AND decided_at IS NOT NULL)
    )
);

-- One pending request per dissertation: a scholar cannot queue three and wait to
-- see which lands.
CREATE UNIQUE INDEX uq_change_requests_one_pending
    ON change_requests (allocation_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_change_requests_status ON change_requests (status);

-- ---- title bank -------------------------------------------------------------
--
-- Section 4.3: each guide proposes at least three titles with a short abstract,
-- the domain, the expected outcome and a complexity. Section 4.6: a scholar may
-- take one or bring their own, so adopting a banked title only prefills the
-- proposal form -- it approves nothing and binds nobody.

CREATE TABLE banked_titles (
    id               BIGSERIAL PRIMARY KEY,
    supervisor_id    BIGINT       NOT NULL REFERENCES supervisor_profiles (id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    abstract_text    TEXT         NOT NULL,
    domain           VARCHAR(128) NOT NULL,
    expected_outcome VARCHAR(32)  NOT NULL,   -- reuses the Annexure-2 outcome vocabulary
    complexity       VARCHAR(16)  NOT NULL,   -- BASIC | INTERMEDIATE | ADVANCED
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_banked_titles_supervisor ON banked_titles (supervisor_id);
CREATE INDEX idx_banked_titles_open       ON banked_titles (status) WHERE status = 'OPEN';
