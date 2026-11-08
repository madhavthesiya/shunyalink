ALTER TABLE analytics_pipeline_state
    ADD COLUMN data_policy VARCHAR(16) NOT NULL DEFAULT 'PRESERVE' CHECK (data_policy IN ('PRESERVE', 'NEW_ONLY')),
    ADD COLUMN collection_started_at TIMESTAMPTZ,
    ADD COLUMN collection_id UUID;
ALTER TABLE analytics_pipeline_state ADD CONSTRAINT analytics_collection_identity_required
    CHECK (data_policy <> 'NEW_ONLY' OR (collection_id IS NOT NULL AND collection_started_at IS NOT NULL));

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
