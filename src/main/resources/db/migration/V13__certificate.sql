-- Phase 11: verifiable provenance.
--
-- A certificate seals what was true about one dissertation at the moment it was
-- issued. The digest is computed over the immutable facts -- roll number, topic,
-- guide, every submitted version's SHA-256, the marks, the viva -- and stored
-- here. The public verify page recomputes that digest from live data and compares.
--
-- So the certificate does not assert "these are the marks". It asserts "these are
-- the marks the register held when this was issued", and anything edited
-- afterwards makes the comparison fail. That is the whole point: tamper-evident
-- rather than tamper-proof.

CREATE TABLE certificates (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(24)  NOT NULL UNIQUE,
    allocation_id BIGINT       NOT NULL REFERENCES allocations (id) ON DELETE CASCADE,
    digest        VARCHAR(64)  NOT NULL,
    -- The human-readable facts, frozen. Kept so a verify page can show what the
    -- certificate claimed even when the live record has since moved on.
    payload       JSONB        NOT NULL,
    issued_by     BIGINT                REFERENCES users (id) ON DELETE SET NULL,
    issued_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    revoked_at    TIMESTAMPTZ,
    CONSTRAINT uq_certificates_allocation UNIQUE (allocation_id)
);

CREATE INDEX idx_certificates_code ON certificates (code);
