-- Add auth provider column (LOCAL = email/password, GOOGLE = Google OAuth)
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(10) NOT NULL DEFAULT 'LOCAL';

-- Google users don't have passwords, so make password nullable
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
