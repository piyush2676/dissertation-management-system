-- Phase 9: in-app notifications.
--
-- Written by a listener on the same domain events the audit trail consumes, in
-- the same transaction, so an action that rolls back notifies nobody about
-- something that did not happen.
--
-- link is the page the notification is about, stored rather than derived, so a
-- later change to a route does not silently break every historical row's target.

CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(48)  NOT NULL,
    title        VARCHAR(160) NOT NULL,
    body         VARCHAR(500),
    link         VARCHAR(255),
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The bell counts unread per user on every page render, so that read has its own
-- partial index rather than scanning the recipient's whole history.
CREATE INDEX idx_notifications_unread ON notifications (recipient_id)
    WHERE read_at IS NULL;

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);
