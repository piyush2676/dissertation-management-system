-- =========================================================================
-- V1 -- users, roles, profiles
--
-- Flyway applies this once and records a checksum in flyway_schema_history.
-- DO NOT EDIT after it has run: any change makes the checksum mismatch and
-- every later startup fails. Corrections go in V2__*.sql.
-- =========================================================================

-- Login account.
-- Named "users" rather than "user": user is a reserved word in PostgreSQL
-- and would need quoting in every statement that touches it.
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- One row per role held, so a user can be both SUPERVISOR and REVIEWER.
-- Composite primary key prevents the same role being granted twice.
CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Student-specific fields. Separate table because a supervisor has no roll
-- number and a student has no max_students -- flattening both into users
-- gives a table half full of nulls that mean "wrong kind of person".
CREATE TABLE student_profiles (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    roll_no    VARCHAR(32) NOT NULL UNIQUE,
    programme  VARCHAR(16) NOT NULL,
    department VARCHAR(128),
    batch      VARCHAR(16),
    semester   INT
);

-- Supervisor-specific fields.
-- research_interests is TEXT, not VARCHAR: it is free text, and Phase 7 feeds
-- it to the embedding model for supervisor matching, so truncating it at 255
-- would quietly degrade that.
-- max_students carries a DEFAULT, but Hibernate always sends the column on
-- insert, so the entity field must also be initialised (= 5) or every
-- supervisor is created with capacity 0.
CREATE TABLE supervisor_profiles (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    designation        VARCHAR(128),
    department         VARCHAR(128),
    research_interests TEXT,
    max_students       INT         NOT NULL DEFAULT 5
);

-- Indexes on the foreign keys these tables are always joined by.
CREATE INDEX idx_student_profiles_user ON student_profiles (user_id);
CREATE INDEX idx_supervisor_profiles_user ON supervisor_profiles (user_id);