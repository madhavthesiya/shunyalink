-- Add CP platform usernames to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_type VARCHAR(20) DEFAULT 'NORMAL';
ALTER TABLE users ADD COLUMN IF NOT EXISTS github_username VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS leetcode_username VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS codeforces_username VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS code_chef_handle VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS at_coder_handle VARCHAR(50);

-- Add category to urls table
ALTER TABLE urls ADD COLUMN IF NOT EXISTS category VARCHAR(20) DEFAULT 'GENERAL';
