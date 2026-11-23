package org.lec.blog.module.post.dto;

import java.time.LocalDateTime;

public record BlogPostRow(long id, String userId, String title, String postUrl, String summary, String sourceType,
                          LocalDateTime publishedAt, String username, String displayName, String avatarUrl,
                          String blogTitle) {}
