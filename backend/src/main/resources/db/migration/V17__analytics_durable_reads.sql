-- Daily dimensions cannot support a 24-hour window spanning parts of two UTC days.
CREATE TABLE analytics_hourly_dimensions (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('country', 'referrer', 'device')),
    value VARCHAR(255) NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY (url_id, bucket_start, dimension, value)
);

-- Preserve all-time hashes separately from the shorter retained time-series.
CREATE TABLE analytics_legacy_dimensions (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('country', 'referrer', 'device')),
    value VARCHAR(255) NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY (url_id, dimension, value)
);
CREATE TABLE analytics_legacy_hourly (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY (url_id, bucket_start)
);
CREATE TABLE analytics_legacy_hourly_dimensions (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('country', 'referrer', 'device')),
    value VARCHAR(255) NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY (url_id, bucket_start, dimension, value)
);
CREATE TABLE analytics_pipeline_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    history_ready BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO analytics_pipeline_state (id) VALUES (1);
