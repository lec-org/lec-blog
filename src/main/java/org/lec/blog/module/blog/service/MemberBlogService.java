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
import org.lec.blog.module.blog.dto.MemberBlogItemResponse;
import org.lec.blog.module.blog.dto.MyBlogResponse;
import org.lec.blog.module.blog.dto.UpsertMyBlogRequest;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.lec.blog.module.post.service.RssDiscoveryService;
import org.springframework.context.event.EventListener;
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

    public MemberBlogService(MemberBlogMapper blogs, BlogPostMapper posts, ObjectMapper json,
                             SafeUrlValidator urls, RssDiscoveryService discovery, TransactionTemplate transaction) {
        this.blogs = blogs;
        this.posts = posts;
        this.json = json;
        this.urls = urls;
        this.discovery = discovery;
        this.transaction = transaction;
    }

    public List<MemberBlogItemResponse> list(String q, String tag, String sort) {
        var query = Wrappers.<MemberBlogEntity>lambdaQuery().eq(MemberBlogEntity::getStatus, "ACTIVE");
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

    public MyBlogResponse own(String userId) {
        var blog = blogs.selectById(userId);
        return blog == null ? null : toOwn(blog);
    }

    public MyBlogResponse upsert(AuthUser user, UpsertMyBlogRequest request) {
        String blogUrl = urls.validate(request.blogUrl().strip()).toString();
        String rssUrl = clean(request.rssUrl());
        if (!rssUrl.isEmpty()) {
            rssUrl = urls.validate(rssUrl).toString();
        } else if (request.autoDiscoverRss()) {
            rssUrl = discovery.discover(blogUrl).orElse("");
        }
        String feed = rssUrl.isEmpty() ? null : rssUrl;
        List<String> tags = request.tags() == null ? List.of() : request.tags().stream().map(String::strip).distinct().toList();
        String tagsJson;
        try {
            tagsJson = json.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode blog tags", exception);
        }
        if (tagsJson.length() > 512) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标签保存后的长度不能超过 512 字符");
        }
        return transaction.execute(status -> save(user, request, blogUrl, feed, tagsJson));
    }

    private MyBlogResponse save(AuthUser user, UpsertMyBlogRequest request, String blogUrl, String feed, String tags) {
        var existing = blogs.lock(user.userId());
        var now = Utc.now();
        if (existing == null) {
            var blog = new MemberBlogEntity();
            blog.setUserId(user.userId());
            blog.setUsername(user.username());
            blog.setDisplayName(user.displayName());
            blog.setAvatarUrl(user.avatar());
            blog.setCaptain(user.isCaptain());
            blog.setBlogUrl(blogUrl);
            blog.setBlogTitle(clean(request.blogTitle()));
            blog.setBio(clean(request.bio()));
            blog.setTagsJson(tags);
            blog.setRssUrl(feed);
            blog.setStatus("ACTIVE");
            blog.setCreatedAt(now);
            blog.setUpdatedAt(now);
            blogs.insert(blog);
            identityCache.put(user.userId(), IdentitySnapshot.from(user));
            return toOwn(blog);
        }

        var update = Wrappers.<MemberBlogEntity>lambdaUpdate()
                .eq(MemberBlogEntity::getUserId, user.userId())
                .set(MemberBlogEntity::getUsername, user.username())
                .set(MemberBlogEntity::getDisplayName, user.displayName())
                .set(MemberBlogEntity::getAvatarUrl, user.avatar())
                .set(MemberBlogEntity::isCaptain, user.isCaptain())
                .set(MemberBlogEntity::getBlogUrl, blogUrl)
                .set(MemberBlogEntity::getBlogTitle, clean(request.blogTitle()))
                .set(MemberBlogEntity::getBio, clean(request.bio()))
                .set(MemberBlogEntity::getTagsJson, tags)
                .set(MemberBlogEntity::getRssUrl, feed)
                .set(MemberBlogEntity::getUpdatedAt, now);
        if (!Objects.equals(existing.getRssUrl(), feed)) {
            posts.delete(Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, user.userId()));
            update.set(MemberBlogEntity::getRssEtag, null)
                    .set(MemberBlogEntity::getRssLastModified, null)
                    .set(MemberBlogEntity::isRssInitialized, false)
                    .set(MemberBlogEntity::getLastPostTitle, null)
                    .set(MemberBlogEntity::getLastPostUrl, null)
                    .set(MemberBlogEntity::getLastPostAt, null)
                    .set(MemberBlogEntity::getLastSyncedAt, null)
                    .set(MemberBlogEntity::getSyncError, null);
        }
        blogs.update(update);
        identityCache.put(user.userId(), IdentitySnapshot.from(user));
        return toOwn(blogs.selectById(user.userId()));
    }

    public void delete(String userId) {
        transaction.executeWithoutResult(status -> {
            var existing = blogs.lock(userId);
            if (existing == null) {
                return;
            }
            if ("HIDDEN".equals(existing.getStatus())) {
                throw new BizException(ErrorCode.FORBIDDEN, "博客已被队长下架，请联系队长处理");
            }
            posts.delete(Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, userId));
            blogs.deleteById(userId);
            identityCache.invalidate(userId);
        });
    }

    public void hide(String userId) {
        int changed = blogs.update(Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, userId)
                .set(MemberBlogEntity::getStatus, "HIDDEN").set(MemberBlogEntity::getUpdatedAt, Utc.now()));
        if (changed == 0 && blogs.selectById(userId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    public void restore(String userId) {
        int changed = blogs.update(Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, userId)
                .set(MemberBlogEntity::getStatus, "ACTIVE").set(MemberBlogEntity::getUpdatedAt, Utc.now()));
        if (changed == 0 && blogs.selectById(userId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
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

    private MemberBlogItemResponse item(MemberBlogEntity blog, LocalDateTime now) {
        var latest = blog.getLastPostAt();
        boolean recent = latest != null && !latest.isBefore(now.minusDays(7)) && !latest.isAfter(now.plusHours(12));
        try {
            return new MemberBlogItemResponse(blog.getUserId(), blog.getUsername(), blog.getDisplayName(),
                    blog.getAvatarUrl(), blog.isCaptain(), blog.getBlogUrl(), blog.getBlogTitle(), blog.getBio(),
                    json.readValue(blog.getTagsJson(), TAGS), blog.getRssUrl(), recent, blog.getLastPostTitle(),
                    blog.getLastPostUrl(), Utc.instant(latest), Utc.instant(blog.getUpdatedAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot decode stored blog tags", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private record IdentitySnapshot(String username, String displayName, String avatarUrl, boolean captain) {
        private static IdentitySnapshot from(AuthUser user) {
            return new IdentitySnapshot(user.username(), user.displayName(), user.avatar(), user.isCaptain());
        }
    }
}
