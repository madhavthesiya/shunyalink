CREATE TABLE url_tags (
    url_id BIGINT NOT NULL,
    tag VARCHAR(255) NOT NULL,
    FOREIGN KEY (url_id) REFERENCES urls(id) ON DELETE CASCADE
);

CREATE INDEX idx_url_tags_url_id ON url_tags(url_id);
CREATE INDEX idx_url_tags_tag ON url_tags(tag);
