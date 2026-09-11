-- Phase 10: email confirmation and password reset.
--
-- One table serves both, because the machinery is identical: issue a
-- single-use, expiring secret tied to a user and a purpose.
--
-- Only the SHA-256 of the token is stored. The plaintext exists in the link
-- sent to the address and nowhere else, so a leak of this table hands an
-- attacker nothing usable -- the same reason password_hash is not a password.

CREATE TABLE auth_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    purpose    VARCHAR(32)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auth_tokens_user_purpose ON auth_tokens (user_id, purpose);

-- Null means the address has never been confirmed reachable. Existing accounts
-- are stamped as confirmed below: they came from institute records, and forcing
-- the whole department to re-confirm on the day this ships would be hostile.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

UPDATE users SET email_verified_at = NOW();

-- The one seeded account on a public mail domain stays unconfirmed, which is
-- exactly the case worth confirming and gives the flow something to demonstrate.
UPDATE users SET email_verified_at = NULL WHERE email = 'student4@gmail.com';
