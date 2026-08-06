CREATE TABLE topics (
    id                      BIGSERIAL PRIMARY KEY,
    student_id              BIGINT       NOT NULL REFERENCES student_profiles(id) ON DELETE CASCADE,
    title                   VARCHAR(255) NOT NULL,
    abstract_text           TEXT         NOT NULL,
    keywords                VARCHAR(512),
    proposed_supervisor_id  BIGINT       REFERENCES supervisor_profiles(id) ON DELETE SET NULL,
    status                  VARCHAR(32)  NOT NULL,
    version                 INT          NOT NULL DEFAULT 1,
    decision_reason         TEXT,
    decided_by              BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_topics_student ON topics (student_id);
CREATE INDEX idx_topics_supervisor ON topics (proposed_supervisor_id);
CREATE INDEX idx_topics_status ON topics (status);