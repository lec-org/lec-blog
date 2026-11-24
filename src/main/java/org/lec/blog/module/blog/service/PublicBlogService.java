package org.lec.blog.module.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.lec.blog.common.Utc;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.dto.MemberBlogItemResponse;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.dto.BlogPostItemResponse;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.lec.blog.module.post.service.PublishedPostQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PublicBlogService {
    private final MemberBlogMapper authors;
    private final BlogPostMapper posts;
    private final MemberBlogService blogs;
    private final PublishedPostQuery feed;
    private final LecBlogProperties properties;

    public PublicBlogService(MemberBlogMapper authors, BlogPostMapper posts, MemberBlogService blogs,
                             PublishedPostQuery feed, LecBlogProperties properties) {
        this.authors = authors;
        this.posts = posts;
        this.blogs = blogs;
        this.feed = feed;
        this.properties = properties;
    }

    public Homepage homepage(String slug, int page, int size) {
        var author = requireAuthor(slug);
        return new Homepage(blogs.item(author, Utc.now()), properties.hostedUrl(slug) + "/rss.xml",
                feed.list(author.getUserId(), page, size));
    }

    public Article article(String slug, String postSlug) {
        var author = requireAuthor(slug);
        var post = posts.selectOne(published(author).eq(BlogPostEntity::getSlug, postSlug));
        if (post == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return new Article(post.getSlug(), post.getTitle(), post.getSummary(), post.getCoverUrl(), post.getContentMd(),
                post.getPostUrl(), Utc.instant(post.getPublishedAt()), Utc.instant(post.getUpdatedAt()),
                blogs.item(author, Utc.now()), neighbor(author, post, true), neighbor(author, post, false));
    }

    public RssFeed rss(String slug) {
        var author = requireAuthor(slug);
        var recent = posts.selectList(published(author)
                .select(BlogPostEntity::getTitle, BlogPostEntity::getSummary, BlogPostEntity::getPostUrl,
                        BlogPostEntity::getGuidHash, BlogPostEntity::getPublishedAt)
                .orderByDesc(BlogPostEntity::getPublishedAt, BlogPostEntity::getId).last("LIMIT 20"));
        return new RssFeed(blogs.item(author, Utc.now()), recent);
    }

    public void requireArticle(String slug, String postSlug) {
        if (!posts.exists(published(requireAuthor(slug)).eq(BlogPostEntity::getSlug, postSlug))) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
    }

    public MemberBlogEntity requireAuthor(String slug) {
        var author = authors.selectOne(Wrappers.<MemberBlogEntity>lambdaQuery()
                .eq(MemberBlogEntity::getHostedSlug, slug).eq(MemberBlogEntity::getBlogMode, "HOSTED")
                .eq(MemberBlogEntity::getStatus, "ACTIVE"));
        if (author == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return author;
    }

    private Neighbor neighbor(MemberBlogEntity author, BlogPostEntity current, boolean previous) {
        var date = current.getPublishedAt();
        var query = published(author).select(BlogPostEntity::getSlug, BlogPostEntity::getTitle, BlogPostEntity::getPostUrl)
                .and(range -> range.lt(previous, BlogPostEntity::getPublishedAt, date)
                        .gt(!previous, BlogPostEntity::getPublishedAt, date)
                        .or(tie -> tie.eq(BlogPostEntity::getPublishedAt, date)
                                .lt(previous, BlogPostEntity::getId, current.getId())
                                .gt(!previous, BlogPostEntity::getId, current.getId())))
                .orderBy(true, !previous, BlogPostEntity::getPublishedAt, BlogPostEntity::getId).last("LIMIT 1");
        var neighbor = posts.selectOne(query);
        return neighbor == null ? null : new Neighbor(neighbor.getSlug(), neighbor.getTitle(), neighbor.getPostUrl());
    }

    private static LambdaQueryWrapper<BlogPostEntity> published(MemberBlogEntity author) {
        return BlogPostMapper.publishedFor(author.getUserId(), "NATIVE");
    }

    public record Homepage(MemberBlogItemResponse author, String rssUrl, PageResult<BlogPostItemResponse> posts) {}
    public record Article(String slug, String title, String summary, String coverUrl, String contentMd,
                          String postUrl, Instant publishedAt, Instant updatedAt,
                          MemberBlogItemResponse author, Neighbor previous, Neighbor next) {}
    public record Neighbor(String slug, String title, String postUrl) {}
    public record RssFeed(MemberBlogItemResponse author, List<BlogPostEntity> posts) {}
}
