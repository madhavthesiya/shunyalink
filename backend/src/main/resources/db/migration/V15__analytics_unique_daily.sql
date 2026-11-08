CREATE TABLE analytics_unique_daily (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    sketch BYTEA NOT NULL,
    PRIMARY KEY (url_id, day)
);
