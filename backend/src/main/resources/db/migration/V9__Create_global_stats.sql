CREATE TABLE platform_stats (
    id BIGINT PRIMARY KEY,
    total_clicks BIGINT NOT NULL DEFAULT 0,
    total_links BIGINT NOT NULL DEFAULT 0,
    total_users BIGINT NOT NULL DEFAULT 0
);

-- Initialize with current data
-- ID=1 is the singleton record for the entire platform
INSERT INTO platform_stats (id, total_clicks, total_links, total_users)
VALUES (1, 
        (SELECT COALESCE(SUM(click_count), 0) FROM urls), 
        (SELECT COUNT(*) FROM urls), 
        (SELECT COUNT(*) FROM users));
