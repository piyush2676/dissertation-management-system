
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE student_profiles (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    roll_no    VARCHAR(32) NOT NULL UNIQUE,
    programme  VARCHAR(16) NOT NULL,
    department VARCHAR(128),
    batch      VARCHAR(16),
    semester   INT
);

CREATE TABLE supervisor_profiles (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    designation        VARCHAR(128),
    department         VARCHAR(128),
    research_interests TEXT,
    max_students       INT         NOT NULL DEFAULT 5
);

CREATE INDEX idx_student_profiles_user ON student_profiles (user_id);
CREATE INDEX idx_supervisor_profiles_user ON supervisor_profiles (user_id);