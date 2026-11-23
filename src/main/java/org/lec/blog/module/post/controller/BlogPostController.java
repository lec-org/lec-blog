package org.lec.blog.module.post.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.lec.blog.common.api.ApiResponse;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.module.post.dto.BlogPostItemResponse;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "LecSSO")
public class BlogPostController {
    private final BlogPostMapper posts;

    public BlogPostController(BlogPostMapper posts) {
        this.posts = posts;
    }

    @GetMapping("/blogs/{userId}/posts")
    public ApiResponse<PageResult<BlogPostItemResponse>> byAuthor(@PathVariable @Size(max = 128) String userId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return list(userId, page, size);
    }

    @GetMapping("/posts/recent")
    public ApiResponse<PageResult<BlogPostItemResponse>> recent(@RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return list(null, page, size);
    }

    private ApiResponse<PageResult<BlogPostItemResponse>> list(String userId, int page, int size) {
        var result = posts.listActive(new Page<>(page, size), userId);
        return ApiResponse.ok(new PageResult<>(page, size, result.getTotal(),
                result.getRecords().stream().map(BlogPostItemResponse::from).toList()));
    }
}
