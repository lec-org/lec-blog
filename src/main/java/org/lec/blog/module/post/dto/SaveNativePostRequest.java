package org.lec.blog.module.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveNativePostRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 128) String slug,
        @Size(max = 300) String summary,
        @Size(max = 512) String coverUrl,
        @NotNull @Size(max = 200000) String contentMd,
        @Pattern(regexp = "DRAFT|PUBLISHED") String postStatus) {}
