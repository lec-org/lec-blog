package org.lec.blog.module.post.dto;

import java.time.Instant;
import org.lec.blog.common.Utc;

public record BlogPostItemResponse(long id, String userId, String title, String postUrl, String summary,
                                   String sourceType, Instant publishedAt, String username,
                                   String displayName, String avatarUrl, String blogTitle) {
    public static BlogPostItemResponse from(BlogPostRow row) {
        return new BlogPostItemResponse(row.id(), row.userId(), row.title(), row.postUrl(), row.summary(),
                row.sourceType(), Utc.instant(row.publishedAt()), row.username(), row.displayName(), row.avatarUrl(),
                row.blogTitle());
    }
}
