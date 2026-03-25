-- Drop the old global unique index
DROP INDEX IF EXISTS idx_long_url_non_custom;

-- Create the new, highly-efficient hybrid partial index
-- Only enforce global deduplication for completely anonymous users (user_id IS NULL)
-- This saves space on massive public text-box spam, while allowing registered users to generate their own personal, fully-isolated URLs
CREATE UNIQUE INDEX idx_long_url_non_custom
    ON urls (long_url)
    WHERE is_custom = FALSE AND expiry_time IS NULL AND user_id IS NULL;
