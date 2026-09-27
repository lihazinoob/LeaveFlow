-- Refresh tokens: long-lived, opaque, random strings that let a client obtain new short-lived access tokens.
-- Only a SHA-256 hash of each token is stored, so a leaked database cannot be used to log in.
-- A row is never reused: every refresh revokes the old token and issues a new one (rotation).

CREATE TABLE refresh_tokens (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT      NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,   -- hex-encoded SHA-256
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,                   -- NULL while the token is still usable
    revoked_reason VARCHAR(30),                 -- why it was revoked; only ROTATED tokens trigger reuse detection
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_refresh_tokens_revoked_reason CHECK (revoked_reason IN
        ('ROTATED', 'LOGOUT', 'PASSWORD_CHANGED', 'ACCOUNT_DEACTIVATED', 'REUSE_DETECTED')),
    CONSTRAINT chk_refresh_tokens_revoked_pair CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

CREATE INDEX idx_refresh_tokens_employee ON refresh_tokens (employee_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
