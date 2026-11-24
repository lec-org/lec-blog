package org.lec.blog.module.post.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.UUID;
import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.lec.blog.common.Hashing;
import org.lec.blog.common.Text;
import org.lec.blog.common.Utc;
import org.lec.blog.common.api.PageResult;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.common.security.SafeUrlValidator;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.blog.service.BlogSlugs;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.lec.blog.module.orbit.OrbitAfterCommit.PostPublished;
import org.lec.blog.module.post.dto.NativePostDetail;
import org.lec.blog.module.post.dto.NativePostItem;
import org.lec.blog.module.post.dto.SaveNativePostRequest;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NativePostService {
    private static final List<Extension> SUMMARY_EXTENSIONS = List.of(
            TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create());
    private final Parser markdown = Parser.builder().extensions(SUMMARY_EXTENSIONS).build();
    private final HtmlRenderer html = HtmlRenderer.builder().extensions(SUMMARY_EXTENSIONS).build();
    private final BlogPostMapper posts;
    private final MemberBlogMapper authors;
    private final MemberBlogService blogs;
    private final SafeUrlValidator urls;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    public NativePostService(BlogPostMapper posts, MemberBlogMapper authors, MemberBlogService blogs,
                             SafeUrlValidator urls, TransactionTemplate transaction, ApplicationEventPublisher events) {
        this.posts = posts;
        this.authors = authors;
        this.blogs = blogs;
        this.urls = urls;
        this.transaction = transaction;
        this.events = events;
    }

    public PageResult<NativePostItem> list(String userId, String status, String q, int page, int size) {
        MemberBlogService.requireNotHidden(authors.selectById(userId));
        var query = Wrappers.<BlogPostEntity>lambdaQuery()
                .select(BlogPostEntity::getId, BlogPostEntity::getTitle, BlogPostEntity::getSlug,
                        BlogPostEntity::getSummary, BlogPostEntity::getCoverUrl, BlogPostEntity::getPostUrl,
                        BlogPostEntity::getPostStatus, BlogPostEntity::getPublishedAt, BlogPostEntity::getCreatedAt,
                        BlogPostEntity::getUpdatedAt)
                .eq(BlogPostEntity::getUserId, userId).eq(BlogPostEntity::getSourceType, "NATIVE")
                .eq(!"ALL".equals(status), BlogPostEntity::getPostStatus, status);
        if (q != null && !q.isBlank()) {
            query.apply("(LOCATE(LOWER({0}), LOWER(title)) > 0 OR LOCATE(LOWER({0}), LOWER(summary)) > 0)", q.strip());
        }
        var result = posts.selectPage(new Page<>(page, size),
                query.orderByDesc(BlogPostEntity::getUpdatedAt, BlogPostEntity::getId));
        return new PageResult<>(page, size, result.getTotal(), result.getRecords().stream().map(NativePostItem::from).toList());
    }

    public NativePostDetail detail(String userId, long id) {
        MemberBlogService.requireNotHidden(authors.selectById(userId));
        return NativePostDetail.from(ownedPost(userId, id));
    }

    public NativePostDetail save(String userId, Long id, SaveNativePostRequest request) {
        try {
            return transaction.execute(status -> {
                var author = authors.lock(userId);
                MemberBlogService.requireNotHidden(author);
                var post = id == null ? new BlogPostEntity() : ownedPost(userId, id);
                if (author == null || !"HOSTED".equals(author.getBlogMode())) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "请先开通工作室托管博客");
                }
                return save(author, post, request);
            });
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT);
        }
    }

    private NativePostDetail save(MemberBlogEntity author, BlogPostEntity post, SaveNativePostRequest request) {
        String userId = author.getUserId();
        Long id = post.getId();
        boolean creating = id == null;
        var now = Utc.now();
        String previousStatus = post.getPostStatus();

        String requestedSlug = request.slug() == null ? "" : request.slug().strip();
        String slug;
        if (!requestedSlug.isEmpty()) {
            slug = BlogSlugs.post(requestedSlug);
        } else if (creating) {
            slug = BlogSlugs.automatic(request.title(), "post-" + Utc.instant(now).getEpochSecond(), 128,
                    candidate -> slugTaken(userId, candidate, null));
        } else {
            slug = post.getSlug();
        }
        if (slugTaken(userId, slug, id)) {
            throw new BizException(ErrorCode.CONFLICT);
        }

        String status = request.postStatus() == null ? (creating ? "DRAFT" : previousStatus) : request.postStatus();
        boolean published = "PUBLISHED".equals(status);
        if (published && request.contentMd().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "发布前请填写文章正文");
        }
        String cover = request.coverUrl() == null || request.coverUrl().isBlank()
                ? null : urls.parse(request.coverUrl().strip()).toString();
        String summaryHtml = request.summary() == null || request.summary().isBlank()
                ? html.render(markdown.parse(request.contentMd())) : request.summary();
        boolean firstPublication = published && post.getPublishedAt() == null;
        post.setTitle(request.title().strip());
        post.setSlug(slug);
        post.setPostUrl(author.getBlogUrl() + "/p/" + slug);
        post.setSummary(Text.truncate(Jsoup.parse(summaryHtml).text(), 300));
        post.setCoverUrl(cover);
        post.setContentMd(request.contentMd());
        post.setPostStatus(status);
        post.setUpdatedAt(now);
        if (firstPublication) {
            post.setPublishedAt(now);
        }

        if (creating) {
            post.setUserId(userId);
            post.setGuidHash(Hashing.sha256("native:" + userId + ":" + UUID.randomUUID()));
            post.setSourceType("NATIVE");
            post.setCreatedAt(now);
            posts.insert(post);
        } else {
            posts.update(Wrappers.<BlogPostEntity>lambdaUpdate().eq(BlogPostEntity::getId, id)
                    .set(BlogPostEntity::getTitle, post.getTitle()).set(BlogPostEntity::getSlug, slug)
                    .set(BlogPostEntity::getPostUrl, post.getPostUrl()).set(BlogPostEntity::getSummary, post.getSummary())
                    .set(BlogPostEntity::getCoverUrl, cover).set(BlogPostEntity::getContentMd, post.getContentMd())
                    .set(BlogPostEntity::getPostStatus, status).set(BlogPostEntity::getUpdatedAt, now)
                    .set(BlogPostEntity::getPublishedAt, post.getPublishedAt()));
        }
        if (published || "PUBLISHED".equals(previousStatus)) {
            blogs.refreshLatest(userId, "NATIVE");
        }
        if (firstPublication) {
            events.publishEvent(new PostPublished(author, post));
        }
        return NativePostDetail.from(post);
    }

    public void delete(String userId, long id) {
        transaction.executeWithoutResult(status -> {
            var author = authors.lock(userId);
            MemberBlogService.requireNotHidden(author);
            var post = ownedPost(userId, id);
            posts.deleteById(post.getId());
            if (author != null && "HOSTED".equals(author.getBlogMode()) && "PUBLISHED".equals(post.getPostStatus())) {
                blogs.refreshLatest(userId, "NATIVE");
            }
        });
    }

    private BlogPostEntity ownedPost(String userId, long id) {
        var post = posts.selectOne(Wrappers.<BlogPostEntity>lambdaQuery()
                .eq(BlogPostEntity::getId, id).eq(BlogPostEntity::getUserId, userId)
                .eq(BlogPostEntity::getSourceType, "NATIVE"));
        if (post == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return post;
    }

    private boolean slugTaken(String userId, String slug, Long id) {
        return posts.exists(Wrappers.<BlogPostEntity>lambdaQuery()
                .eq(BlogPostEntity::getUserId, userId).eq(BlogPostEntity::getSlug, slug)
                .ne(id != null, BlogPostEntity::getId, id));
    }
}
