-- Analytics start fresh at a recorded boundary; legacy counters and Redis data are never imported or changed.
-- Both columns are set together when the collection is established, and never reset by the application.
ALTER TABLE analytics_pipeline_state
    ADD COLUMN collection_started_at TIMESTAMPTZ,
    ADD COLUMN collection_id UUID;
ALTER TABLE analytics_pipeline_state ADD CONSTRAINT analytics_collection_identity_complete
    CHECK ((collection_id IS NULL) = (collection_started_at IS NULL));

-- Separate projections leave legacy counters intact and retain platform clicks after link deletion.
CREATE TABLE analytics_link_totals (
    url_id BIGINT PRIMARY KEY REFERENCES urls(id) ON DELETE CASCADE,
    human_clicks BIGINT NOT NULL CHECK (human_clicks >= 0),
    last_accessed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE analytics_platform_totals (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    human_clicks BIGINT NOT NULL DEFAULT 0 CHECK (human_clicks >= 0)
);
INSERT INTO analytics_platform_totals (id) VALUES (1);
