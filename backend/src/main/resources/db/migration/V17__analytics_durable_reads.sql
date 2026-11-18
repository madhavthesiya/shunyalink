-- Daily dimensions cannot support a 24-hour window spanning parts of two UTC days.
CREATE TABLE analytics_hourly_dimensions (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('country', 'referrer', 'device')),
    value VARCHAR(255) NOT NULL,
    clicks BIGINT NOT NULL CHECK (clicks >= 0),
    PRIMARY KEY (url_id, bucket_start, dimension, value)
);

-- Single-row pipeline state; V18 adds the fresh collection identity.
CREATE TABLE analytics_pipeline_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1)
);
INSERT INTO analytics_pipeline_state (id) VALUES (1);
