package org.lec.blog.module.post.dto;

import java.time.Instant;
import org.lec.blog.common.Utc;
import org.lec.blog.module.post.entity.BlogPostEntity;

public record NativePostItem(long id, String title, String slug, String summary, String coverUrl, String postUrl,
                             String postStatus, Instant publishedAt, Instant createdAt, Instant updatedAt) {
    public static NativePostItem from(BlogPostEntity post) {
        return new NativePostItem(post.getId(), post.getTitle(), post.getSlug(), post.getSummary(), post.getCoverUrl(),
                post.getPostUrl(), post.getPostStatus(), Utc.instant(post.getPublishedAt()),
                Utc.instant(post.getCreatedAt()), Utc.instant(post.getUpdatedAt()));
    }
}
