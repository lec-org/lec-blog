package org.lec.blog.module.blog.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.lec.blog.auth.AuthContext;
import org.lec.blog.auth.RequireCaptain;
import org.lec.blog.common.api.ApiResponse;
import org.lec.blog.module.blog.dto.MemberBlogItemResponse;
import org.lec.blog.module.blog.dto.MyBlogResponse;
import org.lec.blog.module.blog.dto.UpsertMyBlogRequest;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.lec.blog.module.post.service.BlogSyncService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "LecSSO")
public class MemberBlogController {
    private final MemberBlogService blogs;
    private final BlogSyncService sync;

    public MemberBlogController(MemberBlogService blogs, BlogSyncService sync) {
        this.blogs = blogs;
        this.sync = sync;
    }

    @GetMapping("/blogs")
    public ApiResponse<List<MemberBlogItemResponse>> list(@RequestParam(required = false) @Size(max = 128) String q,
            @RequestParam(required = false) @Size(max = 24) String tag,
            @RequestParam(defaultValue = "recent") @Pattern(regexp = "recent|updated") String sort) {
        return ApiResponse.ok(blogs.list(q, tag, sort));
    }

    @GetMapping("/blogs/me")
    public ApiResponse<MyBlogResponse> own(HttpServletRequest request) {
        return ApiResponse.ok(blogs.own(AuthContext.user(request).userId()));
    }

    @PutMapping("/blogs/me")
    public ApiResponse<MyBlogResponse> upsert(HttpServletRequest request, @Valid @RequestBody UpsertMyBlogRequest body) {
        var user = AuthContext.user(request);
        var saved = blogs.upsert(user, body);
        if (saved.blog().rssUrl() != null && saved.status().equals("ACTIVE")) {
            sync.enqueue(user.userId());
        }
        return ApiResponse.ok(saved);
    }

    @DeleteMapping("/blogs/me")
    public ApiResponse<Void> delete(HttpServletRequest request) {
        blogs.delete(AuthContext.user(request).userId());
        return ApiResponse.ok(null);
    }

    @PostMapping("/blogs/me/sync")
    public ApiResponse<Void> sync(HttpServletRequest request) {
        sync.requestManual(AuthContext.user(request).userId());
        return ApiResponse.ok(null);
    }

    @RequireCaptain
    @DeleteMapping("/admin/blogs/{userId}")
    public ApiResponse<Void> hide(@PathVariable @Size(max = 128) String userId) {
        blogs.hide(userId);
        return ApiResponse.ok(null);
    }
}
