CREATE SEQUENCE IF NOT EXISTS url_id_seq START 1 INCREMENT 1;

CREATE TABLE IF NOT EXISTS urls (
                                    id                 BIGINT PRIMARY KEY DEFAULT nextval('url_id_seq'),
    short_id           VARCHAR(20) UNIQUE,
    long_url           VARCHAR(2048) NOT NULL,
    is_custom          BOOLEAN NOT NULL DEFAULT FALSE,
    click_count        BIGINT NOT NULL DEFAULT 0,
    last_accessed_time TIMESTAMP,
    expiry_time        TIMESTAMP,
    created_at         TIMESTAMP NOT NULL
    );

-- Idempotency: same permanent non-custom URL always gets same shortId
CREATE UNIQUE INDEX IF NOT EXISTS idx_long_url_non_custom
    ON urls (long_url)
    WHERE is_custom = FALSE AND expiry_time IS NULL;
