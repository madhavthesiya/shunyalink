-- Roadmap 3.4c: a link's status. ACTIVE links redirect. FLAGGED links (found unsafe after creation, by the AI check
-- or a later Safe Browsing re-check) show a warning page instead, and never reveal their destination.
--
-- Expand-only, so a rollback still works: the previous build does not map the column, and its inserts get the default.
-- PostgreSQL 11+ adds a column with a constant default without rewriting the table.
ALTER TABLE urls ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE urls ADD CONSTRAINT urls_status_check CHECK (status IN ('ACTIVE', 'FLAGGED'));
