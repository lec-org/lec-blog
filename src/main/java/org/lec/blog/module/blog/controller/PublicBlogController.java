package org.lec.blog.module.blog.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.lec.blog.auth.AuthInterceptor;
import org.lec.blog.common.api.ApiResponse;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.dto.MemberBlogItemResponse;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.lec.blog.module.blog.service.PublicBlogService;
import org.lec.blog.module.post.dto.BlogPostItemResponse;
import org.lec.blog.module.post.service.PublishedPostQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicBlogController {
    private final LecBlogProperties properties;
    private final AuthInterceptor auth;
    private final MemberBlogService members;
    private final PublishedPostQuery posts;
    private final PublicBlogService hosted;

    public PublicBlogController(LecBlogProperties properties, AuthInterceptor auth, MemberBlogService members,
                                PublishedPostQuery posts, PublicBlogService hosted) {
        this.properties = properties;
        this.auth = auth;
        this.members = members;
        this.posts = posts;
        this.hosted = hosted;
    }

    @GetMapping("/config")
    public ApiResponse<WebConfig> config() {
        var sso = properties.auth();
        return ApiResponse.ok(new WebConfig(sso.issuer(), sso.orgId(), sso.webClientId(), properties.publicUrl(),
                auth.mockEnabled(), properties.storage().enabled()));
    }

    @GetMapping("/blogs")
    public ApiResponse<List<MemberBlogItemResponse>> blogs(@RequestParam(required = false) @Size(max = 128) String q,
            @RequestParam(required = false) @Size(max = 24) String tag,
            @RequestParam(defaultValue = "recent") @Pattern(regexp = "recent|updated") String sort,
            @RequestParam(defaultValue = "ALL") @Pattern(regexp = "ALL|HOSTED|EXTERNAL") String mode) {
        return ApiResponse.ok(members.list(q, tag, sort, mode));
    }

    @GetMapping("/posts/recent")
    public ApiResponse<PageResult<BlogPostItemResponse>> recent(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(posts.list(null, page, size));
    }

    @GetMapping("/u/{hostedSlug}")
    public ApiResponse<PublicBlogService.Homepage> homepage(@PathVariable String hostedSlug,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(hosted.homepage(hostedSlug, page, size));
    }

    @GetMapping("/u/{hostedSlug}/posts/{postSlug}")
    public ApiResponse<PublicBlogService.Article> article(@PathVariable String hostedSlug, @PathVariable String postSlug) {
        return ApiResponse.ok(hosted.article(hostedSlug, postSlug));
    }

    public record WebConfig(String issuer, String orgId, String webClientId, String publicUrl,
                            boolean mockEnabled, boolean imageUploadEnabled) {}
}
