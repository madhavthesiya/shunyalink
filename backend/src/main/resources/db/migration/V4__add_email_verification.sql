-- Add email verified flag to users
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Email verification tokens
CREATE TABLE verification_tokens (
                                     id          BIGSERIAL PRIMARY KEY,
                                     token       VARCHAR(36) UNIQUE NOT NULL,
                                     user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                     expires_at  TIMESTAMP NOT NULL,
                                     created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
                                       id          BIGSERIAL PRIMARY KEY,
                                       token       VARCHAR(36) UNIQUE NOT NULL,
                                       user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       expires_at  TIMESTAMP NOT NULL,
                                       used        BOOLEAN NOT NULL DEFAULT FALSE,
                                       created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
