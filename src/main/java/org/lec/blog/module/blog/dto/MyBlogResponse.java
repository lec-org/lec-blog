package org.lec.blog.module.blog.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.time.Instant;

public record MyBlogResponse(@JsonUnwrapped MemberBlogItemResponse blog, String status,
                             Instant lastSyncedAt, String syncError) {}
