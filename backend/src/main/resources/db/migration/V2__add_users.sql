-- Create users table
CREATE TABLE users (
                       id         BIGSERIAL PRIMARY KEY,
                       email      VARCHAR(255) UNIQUE NOT NULL,
                       password   VARCHAR(255) NOT NULL,
                       name       VARCHAR(100) NOT NULL,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Link URLs to users (nullable — existing URLs stay anonymous)
ALTER TABLE urls ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_urls_user_id ON urls(user_id);
