package org.lec.blog.module.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EnableHostedBlogRequest(
        @Size(max = 32) String hostedSlug,
        @Size(max = 128) String blogTitle,
        @Size(max = 300) String bio,
        @Size(max = 8) List<@NotBlank @Size(max = 24) String> tags) {}
