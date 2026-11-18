-- Existing UUID-only markers remain unverifiable: never guess or overwrite their payload identity.
ALTER TABLE analytics_applied_events ADD COLUMN payload_hash VARCHAR(64);
