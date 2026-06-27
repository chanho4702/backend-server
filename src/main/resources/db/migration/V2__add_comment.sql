CREATE TABLE comment (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT NOT NULL REFERENCES post(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    author_id   BIGINT NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);
CREATE INDEX idx_comment_post ON comment(post_id);
