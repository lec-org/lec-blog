package org.lec.blog.module.blog.dto;

import java.time.Instant;
import java.util.List;

public record MemberBlogItemResponse(String userId, String username, String displayName, String avatarUrl,
                                     boolean isCaptain, String blogUrl, String blogTitle, String bio,
                                     List<String> tags, String rssUrl, boolean hasRecentPost,
                                     String latestPostTitle, String latestPostUrl, Instant latestPostAt,
                                     Instant updatedAt) {}
