package org.lec.blog.module.post.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.lec.blog.auth.AuthContext;
import org.lec.blog.common.api.ApiResponse;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.module.post.dto.NativePostDetail;
import org.lec.blog.module.post.dto.NativePostItem;
import org.lec.blog.module.post.dto.SaveNativePostRequest;
import org.lec.blog.module.post.service.NativePostService;
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
@RequestMapping("/api/v1/posts/me")
@SecurityRequirement(name = "LecSSO")
public class NativePostController {
    private final NativePostService posts;

    public NativePostController(NativePostService posts) {
        this.posts = posts;
    }

    @GetMapping
    public ApiResponse<PageResult<NativePostItem>> list(HttpServletRequest request,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|DRAFT|PUBLISHED") String status,
            @RequestParam(required = false) @Size(max = 128) String q,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(posts.list(AuthContext.user(request).userId(), status, q, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<NativePostDetail> detail(HttpServletRequest request, @PathVariable @Min(1) long id) {
        return ApiResponse.ok(posts.detail(AuthContext.user(request).userId(), id));
    }

    @PostMapping
    public ApiResponse<NativePostDetail> create(HttpServletRequest request,
            @Valid @RequestBody SaveNativePostRequest body) {
        return ApiResponse.ok(posts.save(AuthContext.user(request).userId(), null, body));
    }

    @PutMapping("/{id}")
    public ApiResponse<NativePostDetail> update(HttpServletRequest request, @PathVariable @Min(1) long id,
            @Valid @RequestBody SaveNativePostRequest body) {
        return ApiResponse.ok(posts.save(AuthContext.user(request).userId(), id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable @Min(1) long id) {
        posts.delete(AuthContext.user(request).userId(), id);
        return ApiResponse.ok(null);
    }
}
