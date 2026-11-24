package org.lec.blog.module.blog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.lec.blog.auth.AuthUser;
import org.lec.blog.common.Utc;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.common.security.SafeUrlValidator;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.dto.EnableHostedBlogRequest;
import org.lec.blog.module.blog.dto.MemberBlogItemResponse;
import org.lec.blog.module.blog.dto.MyBlogResponse;
import org.lec.blog.module.blog.dto.UpsertMyBlogRequest;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.orbit.OrbitAfterCommit.BlogJoined;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.lec.blog.module.post.service.RssDiscoveryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MemberBlogService {
    private static final TypeReference<List<String>> TAGS = new TypeReference<>() {};
    private final Cache<String, IdentitySnapshot> identityCache = Caffeine.newBuilder()
            .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(5)).build();
    private final MemberBlogMapper blogs;
    private final BlogPostMapper posts;
    private final ObjectMapper json;
    private final SafeUrlValidator urls;
    private final RssDiscoveryService discovery;
    private final TransactionTemplate transaction;
    private final LecBlogProperties properties;
    private final ApplicationEventPublisher events;

    public MemberBlogService(MemberBlogMapper blogs, BlogPostMapper posts, ObjectMapper json,
                             SafeUrlValidator urls, RssDiscoveryService discovery, TransactionTemplate transaction,
                             LecBlogProperties properties, ApplicationEventPublisher events) {
        this.blogs = blogs;
        this.posts = posts;
        this.json = json;
        this.urls = urls;
        this.discovery = discovery;
        this.transaction = transaction;
        this.properties = properties;
        this.events = events;
    }

    public List<MemberBlogItemResponse> list(String q, String tag, String sort, String mode) {
        var query = Wrappers.<MemberBlogEntity>lambdaQuery().eq(MemberBlogEntity::getStatus, "ACTIVE")
                .eq(!"ALL".equals(mode), MemberBlogEntity::getBlogMode, mode);
        if (q != null && !q.isBlank()) {
            query.apply("(LOCATE(LOWER({0}), LOWER(display_name)) > 0 OR "
                    + "LOCATE(LOWER({0}), LOWER(username)) > 0 OR "
                    + "LOCATE(LOWER({0}), LOWER(blog_title)) > 0 OR LOCATE(LOWER({0}), LOWER(bio)) > 0)", q.strip());
        }
        if (sort.equals("recent")) {
            query.orderByDesc(MemberBlogEntity::getLastPostAt);
        }
        query.orderByDesc(MemberBlogEntity::getUpdatedAt).orderByAsc(MemberBlogEntity::getUserId);
        var now = Utc.now();
        return blogs.selectList(query).stream().map(blog -> item(blog, now))
                .filter(blog -> tag == null || tag.isBlank()
                        || blog.tags().stream().anyMatch(value -> value.equalsIgnoreCase(tag.strip())))
                .toList();
    }

    public List<MemberBlogItemResponse> listHidden() {
        var query = Wrappers.<MemberBlogEntity>lambdaQuery()
                .eq(MemberBlogEntity::getStatus, "HIDDEN")
                .orderByDesc(MemberBlogEntity::getUpdatedAt)
                .orderByAsc(MemberBlogEntity::getUserId);
        var now = Utc.now();
        return blogs.selectList(query).stream().map(blog -> item(blog, now)).toList();
    }

    public MyBlogResponse own(String userId) {
        var blog = blogs.selectById(userId);
        return blog == null ? null : toOwn(blog);
    }

    public MyBlogResponse upsert(AuthUser user, UpsertMyBlogRequest request) {
        var current = blogs.selectById(user.userId());
        requireNotHidden(current);
        var profile = profile(request.blogTitle(), request.bio(), request.tags());

        String requestedUrl = request.blogUrl().strip();
        if (current != null && "HOSTED".equals(current.getBlogMode()) && requestedUrl.equals(current.getBlogUrl())) {
            return transaction.execute(status -> {
                var existing = blogs.lock(user.userId());
                requireNotHidden(existing);
                if (existing == null || !"HOSTED".equals(existing.getBlogMode())
                        || !requestedUrl.equals(existing.getBlogUrl())) {
                    throw new BizException(ErrorCode.CONFLICT, "博客配置已变更，请刷新后再提交");
                }
                return save(user, existing, profile, requestedUrl, null, existing.getHostedSlug());
            });
        }

        String blogUrl = urls.validate(requestedUrl).toString();
        String rssUrl = clean(request.rssUrl());
        if (!rssUrl.isEmpty()) {
            rssUrl = urls.validate(rssUrl).toString();
        } else if (request.autoDiscoverRss()) {
            rssUrl = discovery.discover(blogUrl).orElse("");
        }
        String feed = rssUrl.isEmpty() ? null : rssUrl;
        try {
            return transaction.execute(status -> save(user, blogs.lock(user.userId()), profile, blogUrl, feed, null));
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT, "博客配置已变更，请刷新后再提交");
        }
    }

    public MyBlogResponse enableHosted(AuthUser user, EnableHostedBlogRequest request) {
        var profile = profile(request.blogTitle(), request.bio(), request.tags());
        try {
            return transaction.execute(status -> {
                var existing = blogs.lock(user.userId());
                requireNotHidden(existing);
                String slug = clean(request.hostedSlug());
                if (slug.isEmpty()) {
                    slug = existing != null && existing.getHostedSlug() != null ? existing.getHostedSlug()
                            : BlogSlugs.automatic(user.username(), "member", 32, value -> slugTaken(user.userId(), value));
                } else if (slugTaken(user.userId(), BlogSlugs.hosted(slug))) {
                    throw new BizException(ErrorCode.CONFLICT);
                }
                return save(user, existing, profile, properties.hostedUrl(slug), null, slug);
            });
        } catch (DuplicateKeyException exception) {
            throw new BizException(ErrorCode.CONFLICT);
        }
    }

    private boolean slugTaken(String userId, String slug) {
        return blogs.exists(Wrappers.<MemberBlogEntity>lambdaQuery()
                .eq(MemberBlogEntity::getHostedSlug, slug).ne(MemberBlogEntity::getUserId, userId));
    }

    private MyBlogResponse save(AuthUser user, MemberBlogEntity existing, Profile profile,
                                String blogUrl, String rssUrl, String hostedSlug) {
        requireNotHidden(existing);
        String userId = user.userId();
        var now = Utc.now();
        boolean hosted = hostedSlug != null;
        String mode = hosted ? "HOSTED" : "EXTERNAL";
        boolean resetFeed = existing != null && (!mode.equals(existing.getBlogMode())
                || !Objects.equals(existing.getRssUrl(), rssUrl));
        if (existing == null) {
            var author = new MemberBlogEntity();
            author.setUserId(userId);
            author.setUsername(user.username());
            author.setDisplayName(user.displayName());
            author.setAvatarUrl(user.avatar());
            author.setCaptain(user.isCaptain());
            author.setBlogUrl(blogUrl);
            author.setBlogTitle(profile.title());
            author.setBio(profile.bio());
            author.setTagsJson(profile.tags());
            author.setRssUrl(rssUrl);
            author.setStatus("ACTIVE");
            author.setBlogMode(mode);
            author.setHostedSlug(hostedSlug);
            author.setCreatedAt(now);
            author.setUpdatedAt(now);
            blogs.insert(author);
            if (blogs.markFirstJoin(userId) == 1) {
                events.publishEvent(new BlogJoined(author));
            }
        } else {
            var update = Wrappers.<MemberBlogEntity>lambdaUpdate()
                    .eq(MemberBlogEntity::getUserId, userId)
                    .set(MemberBlogEntity::getUsername, user.username())
                    .set(MemberBlogEntity::getDisplayName, user.displayName())
                    .set(MemberBlogEntity::getAvatarUrl, user.avatar())
                    .set(MemberBlogEntity::isCaptain, user.isCaptain())
                    .set(MemberBlogEntity::getBlogMode, mode)
                    .set(hosted, MemberBlogEntity::getHostedSlug, hostedSlug)
                    .set(MemberBlogEntity::getBlogUrl, blogUrl)
                    .set(MemberBlogEntity::getBlogTitle, profile.title())
                    .set(MemberBlogEntity::getBio, profile.bio())
                    .set(MemberBlogEntity::getTagsJson, profile.tags())
                    .set(MemberBlogEntity::getRssUrl, rssUrl)
                    .set(MemberBlogEntity::getUpdatedAt, now);
            if (resetFeed) {
                deleteRss(userId);
                update.set(MemberBlogEntity::getRssEtag, null)
                        .set(MemberBlogEntity::getRssLastModified, null)
                        .set(MemberBlogEntity::isRssInitialized, false)
                        .set(MemberBlogEntity::getLastSyncedAt, null)
                        .set(MemberBlogEntity::getSyncError, null);
            }
            blogs.update(update);
        }
        if (hosted && (existing == null || !blogUrl.equals(existing.getBlogUrl()))) {
            posts.update(Wrappers.<BlogPostEntity>lambdaUpdate()
                    .eq(BlogPostEntity::getUserId, userId).eq(BlogPostEntity::getSourceType, "NATIVE")
                    .setSql("post_url = CONCAT({0}, slug)", blogUrl + "/p/"));
        }
        if (hosted || resetFeed) {
            refreshLatest(userId, hosted ? "NATIVE" : "RSS");
        }
        identityCache.put(userId, IdentitySnapshot.from(user));
        return toOwn(blogs.selectById(userId));
    }

    public void refreshLatest(String userId, String sourceType) {
        var newest = posts.latestPublished(userId, sourceType);
        blogs.update(Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, userId)
                .set(MemberBlogEntity::getLastPostTitle, newest == null ? null : newest.getTitle())
                .set(MemberBlogEntity::getLastPostUrl, newest == null ? null : newest.getPostUrl())
                .set(MemberBlogEntity::getLastPostAt, newest == null ? null : newest.getPublishedAt()));
    }

    private void deleteRss(String userId) {
        posts.delete(Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, userId)
                .eq(BlogPostEntity::getSourceType, "RSS"));
    }

    public void delete(String userId) {
        transaction.executeWithoutResult(status -> {
            var existing = blogs.lock(userId);
            if (existing == null) {
                return;
            }
            requireNotHidden(existing);
            deleteRss(userId);
            blogs.deleteById(userId);
            identityCache.invalidate(userId);
        });
    }

    public void hide(String userId) {
        setStatus(userId, "HIDDEN");
    }

    public void restore(String userId) {
        setStatus(userId, "ACTIVE");
        identityCache.invalidate(userId);
    }

    private void setStatus(String userId, String status) {
        int changed = blogs.update(Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, userId)
                .set(MemberBlogEntity::getStatus, status).set(MemberBlogEntity::getUpdatedAt, Utc.now()));
        if (changed == 0 && blogs.selectById(userId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    public static void requireNotHidden(MemberBlogEntity author) {
        if (author != null && "HIDDEN".equals(author.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "博客已被队长下架，请联系队长处理");
        }
    }

    @Async
    @EventListener
    public void refreshIdentity(AuthUser user) {
        var snapshot = IdentitySnapshot.from(user);
        if (snapshot.equals(identityCache.getIfPresent(user.userId()))) {
            return;
        }
        var update = Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, user.userId())
                .eq(MemberBlogEntity::getStatus, "ACTIVE")
                .and(diff -> diff.ne(MemberBlogEntity::isCaptain, user.isCaptain())
                        .or(!user.username().isEmpty(), username -> username.ne(MemberBlogEntity::getUsername, user.username()))
                        .or(!user.displayName().isEmpty(), name -> name.ne(MemberBlogEntity::getDisplayName, user.displayName()))
                        .or(!user.avatar().isEmpty(), avatar -> avatar.ne(MemberBlogEntity::getAvatarUrl, user.avatar())))
                .set(MemberBlogEntity::isCaptain, user.isCaptain())
                .set(!user.username().isEmpty(), MemberBlogEntity::getUsername, user.username())
                .set(!user.displayName().isEmpty(), MemberBlogEntity::getDisplayName, user.displayName())
                .set(!user.avatar().isEmpty(), MemberBlogEntity::getAvatarUrl, user.avatar())
                .set(MemberBlogEntity::getUpdatedAt, Utc.now());
        blogs.update(update);
        identityCache.put(user.userId(), snapshot);
    }

    private MyBlogResponse toOwn(MemberBlogEntity blog) {
        return new MyBlogResponse(item(blog, Utc.now()), blog.getStatus(), Utc.instant(blog.getLastSyncedAt()),
                blog.getSyncError());
    }

    public MemberBlogItemResponse item(MemberBlogEntity blog, LocalDateTime now) {
        var latest = blog.getLastPostAt();
        boolean recent = latest != null && !latest.isBefore(now.minusDays(7)) && !latest.isAfter(now.plusHours(12));
        try {
            return new MemberBlogItemResponse(blog.getUserId(), blog.getUsername(), blog.getDisplayName(),
                    blog.getAvatarUrl(), blog.isCaptain(), blog.getBlogUrl(), blog.getBlogTitle(), blog.getBio(),
                    json.readValue(blog.getTagsJson(), TAGS), blog.getRssUrl(), recent, blog.getLastPostTitle(),
                    blog.getLastPostUrl(), Utc.instant(latest), Utc.instant(blog.getUpdatedAt()),
                    blog.getBlogMode(), blog.getHostedSlug());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot decode stored blog tags", exception);
        }
    }

    private Profile profile(String title, String bio, List<String> requestTags) {
        List<String> tags = requestTags == null ? List.of() : requestTags.stream().map(String::strip).distinct().toList();
        try {
            String tagsJson = json.writeValueAsString(tags);
            if (tagsJson.length() > 512) {
                throw new BizException(ErrorCode.BAD_REQUEST, "标签保存后的长度不能超过 512 字符");
            }
            return new Profile(clean(title), clean(bio), tagsJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode blog tags", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private record Profile(String title, String bio, String tags) {}

    private record IdentitySnapshot(String username, String displayName, String avatarUrl, boolean captain) {
        private static IdentitySnapshot from(AuthUser user) {
            return new IdentitySnapshot(user.username(), user.displayName(), user.avatar(), user.isCaptain());
        }
    }
}
