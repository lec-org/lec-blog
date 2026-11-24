ALTER TABLE member_blog ADD COLUMN blog_mode VARCHAR(16) NOT NULL DEFAULT 'EXTERNAL';
ALTER TABLE member_blog ADD COLUMN hosted_slug VARCHAR(64) NULL;
ALTER TABLE member_blog ADD COLUMN theme_json VARCHAR(1024) NOT NULL DEFAULT '{}';

CREATE UNIQUE INDEX uk_member_blog_hosted_slug ON member_blog (hosted_slug);

ALTER TABLE blog_post ADD COLUMN slug VARCHAR(128) NULL;
ALTER TABLE blog_post ADD COLUMN content_md LONGTEXT NULL;
ALTER TABLE blog_post ADD COLUMN cover_url VARCHAR(512) NULL;
ALTER TABLE blog_post ADD COLUMN post_status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED';
ALTER TABLE blog_post ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
ALTER TABLE blog_post MODIFY COLUMN published_at DATETIME(3) NULL;

CREATE UNIQUE INDEX uk_blog_post_user_slug ON blog_post (user_id, slug);
CREATE INDEX idx_blog_post_user_mode_status_pub
    ON blog_post (user_id, source_type, post_status, published_at DESC);

CREATE TABLE blog_member_joined (
    user_id VARCHAR(128) ${user-id-collation} NOT NULL PRIMARY KEY
) ${table-options};

INSERT INTO blog_member_joined (user_id) SELECT user_id FROM member_blog;
