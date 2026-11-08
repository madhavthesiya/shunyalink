-- Additive storage for the future pipeline. Existing Redis analytics remain untouched.
CREATE TABLE analytics_applied_events (
    event_id UUID PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analytics_hourly (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    human_clicks BIGINT NOT NULL DEFAULT 0 CHECK (human_clicks >= 0),
    bot_clicks BIGINT NOT NULL DEFAULT 0 CHECK (bot_clicks >= 0),
    PRIMARY KEY (url_id, bucket_start)
);

CREATE TABLE analytics_daily_dimensions (
    url_id BIGINT NOT NULL REFERENCES urls(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    dimension VARCHAR(16) NOT NULL CHECK (dimension IN ('country', 'referrer', 'device')),
    value VARCHAR(255) NOT NULL,
    clicks BIGINT NOT NULL DEFAULT 0 CHECK (clicks >= 0),
    PRIMARY KEY (url_id, day, dimension, value)
);
