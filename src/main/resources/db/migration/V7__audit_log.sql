-- Phase 4b: the audit trail.
--
-- Every state change in the workflow writes one row here, in the same
-- transaction as the change itself, so an action that rolls back leaves no
-- audit entry and an entry can never describe something that did not happen.
--
-- actor is nullable so a future system-initiated action still records.

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT               REFERENCES users (id) ON DELETE SET NULL,
    actor_email VARCHAR(255) NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    old_value   VARCHAR(255),
    new_value   VARCHAR(255),
    at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_at     ON audit_log (at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_actor  ON audit_log (actor_email);
