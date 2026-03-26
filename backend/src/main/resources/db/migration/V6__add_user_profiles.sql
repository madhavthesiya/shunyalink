-- Expand users table for Link-in-Bio profiles
ALTER TABLE users ADD COLUMN username VARCHAR(50) UNIQUE;
ALTER TABLE users ADD COLUMN bio_text VARCHAR(500);
ALTER TABLE users ADD COLUMN theme_color VARCHAR(20) DEFAULT '#000000';
-- Expand urls table to toggle visibility on the public bio page
ALTER TABLE urls ADD COLUMN show_on_bio BOOLEAN NOT NULL DEFAULT FALSE;