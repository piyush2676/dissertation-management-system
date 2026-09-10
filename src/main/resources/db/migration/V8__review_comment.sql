-- Phase 5: review comments.
--
-- Comments hang off a submission_version, not off the submission, so feedback
-- stays pinned to the exact file it was written against. A later version starts
-- with a clean sheet and the old thread stays readable next to the old file.

CREATE TABLE review_comments (
    id                    BIGSERIAL PRIMARY KEY,
    submission_version_id BIGINT      NOT NULL REFERENCES submission_versions (id) ON DELETE CASCADE,
    reviewer_id           BIGINT      NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    page_no               INT,
    body                  TEXT        NOT NULL,
    resolved              BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_review_comments_page CHECK (page_no IS NULL OR page_no > 0),
    CONSTRAINT ck_review_comments_resolved_at
        CHECK ((resolved AND resolved_at IS NOT NULL) OR (NOT resolved AND resolved_at IS NULL))
);

CREATE INDEX idx_review_comments_version  ON review_comments (submission_version_id);
CREATE INDEX idx_review_comments_resolved ON review_comments (resolved);
