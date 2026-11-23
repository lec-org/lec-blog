package org.lec.blog.module.post.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.lec.blog.common.Text;
import org.lec.blog.common.Utc;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.common.security.SafeHttpClient;
import org.lec.blog.common.security.SafeUrlValidator;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.orbit.OrbitEventClient;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BlogSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(BlogSyncService.class);
    private final Cache<String, Boolean> manualCooldown = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5)).build();
    private final MemberBlogMapper blogs;
    private final BlogPostMapper posts;
    private final SafeHttpClient http;
    private final SafeUrlValidator urls;
    private final OrbitEventClient orbit;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;

    public BlogSyncService(MemberBlogMapper blogs, BlogPostMapper posts, SafeHttpClient http, SafeUrlValidator urls,
                           OrbitEventClient orbit, TransactionTemplate transaction, ApplicationEventPublisher events) {
        this.blogs = blogs;
        this.posts = posts;
        this.http = http;
        this.urls = urls;
        this.orbit = orbit;
        this.transaction = transaction;
        this.events = events;
    }

    public void requestManual(String userId) {
        var blog = blogs.selectById(userId);
        if (blog == null || !blog.getStatus().equals("ACTIVE") || blog.getRssUrl() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先为有效博客配置订阅源");
        }
        if (manualCooldown.asMap().putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS);
        }
        enqueue(userId);
    }

    public void enqueue(String userId) {
        events.publishEvent(new SyncRequested(userId));
    }

    @Async
    @EventListener
    public void onRequested(SyncRequested request) {
        sync(request.userId());
    }

    public void sync(String userId) {
        var snapshot = blogs.selectById(userId);
        if (snapshot == null || !snapshot.getStatus().equals("ACTIVE") || snapshot.getRssUrl() == null) {
            return;
        }
        SafeHttpClient.Result response;
        List<BlogPostEntity> feedItems;
        LocalDateTime now = Utc.now();
        try {
            response = http.get(snapshot.getRssUrl(), snapshot.getRssEtag(), snapshot.getRssLastModified());
            if (response.status() == 304 && !snapshot.isRssInitialized()) {
                throw new IOException("首次同步未收到订阅源数据");
            }
            feedItems = response.status() == 304 ? List.of() : parseFeed(response, userId, now);
        } catch (IOException | FeedException | BizException exception) {
            String error = exception instanceof FeedException ? "订阅源格式无效" : exception.getMessage();
            LOG.warn("博客订阅同步失败，作者 ID {}，原因 {}", userId, error);
            transaction.executeWithoutResult(status -> {
                if (stillCurrent(snapshot, blogs.lock(userId))) {
                    blogs.update(syncUpdate(userId, Utc.now()).set(MemberBlogEntity::getSyncError, Text.truncate(error, 255)));
                }
            });
            return;
        }

        var notification = transaction.execute(status -> store(snapshot, response, feedItems, Utc.now()));
        if (notification != null) {
            orbit.publish(notification.author(), notification.post());
        }
    }

    private Notification store(MemberBlogEntity snapshot, SafeHttpClient.Result response, List<BlogPostEntity> items,
                               LocalDateTime now) {
        String userId = snapshot.getUserId();
        var current = blogs.lock(userId);
        if (!stillCurrent(snapshot, current)) {
            return null;
        }
        var update = syncUpdate(userId, now).set(MemberBlogEntity::getSyncError, null);
        String etag = header(response.etag());
        String lastModified = header(response.lastModified());
        if (response.status() == 304) {
            blogs.update(update.set(etag != null, MemberBlogEntity::getRssEtag, etag)
                    .set(lastModified != null, MemberBlogEntity::getRssLastModified, lastModified));
            return null;
        }

        var known = new HashSet<String>();
        if (!items.isEmpty()) {
            posts.selectList(Wrappers.<BlogPostEntity>lambdaQuery().select(BlogPostEntity::getGuidHash)
                    .eq(BlogPostEntity::getUserId, userId)
                    .in(BlogPostEntity::getGuidHash, items.stream().map(BlogPostEntity::getGuidHash).distinct().toList()))
                    .forEach(post -> known.add(post.getGuidHash()));
        }
        BlogPostEntity newestNewPost = null;
        for (var post : items) {
            if (known.add(post.getGuidHash())) {
                posts.insert(post);
                if (!post.getPublishedAt().isBefore(now.minusDays(7)) && !post.getPublishedAt().isAfter(now)
                        && (newestNewPost == null || post.getPublishedAt().isAfter(newestNewPost.getPublishedAt()))) {
                    newestNewPost = post;
                }
            }
        }
        var newest = posts.selectOne(Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, userId)
                .orderByDesc(BlogPostEntity::getPublishedAt, BlogPostEntity::getId).last("LIMIT 1"));
        blogs.update(update.set(MemberBlogEntity::getRssEtag, etag)
                .set(MemberBlogEntity::getRssLastModified, lastModified)
                .set(MemberBlogEntity::isRssInitialized, true)
                .set(MemberBlogEntity::getLastPostTitle, newest == null ? null : newest.getTitle())
                .set(MemberBlogEntity::getLastPostUrl, newest == null ? null : newest.getPostUrl())
                .set(MemberBlogEntity::getLastPostAt, newest == null ? null : newest.getPublishedAt()));
        return current.isRssInitialized() && newestNewPost != null ? new Notification(current, newestNewPost) : null;
    }

    private static LambdaUpdateWrapper<MemberBlogEntity> syncUpdate(String userId, LocalDateTime now) {
        return Wrappers.<MemberBlogEntity>lambdaUpdate().eq(MemberBlogEntity::getUserId, userId)
                .set(MemberBlogEntity::getLastSyncedAt, now).setSql("updated_at = updated_at");
    }

    private static boolean stillCurrent(MemberBlogEntity snapshot, MemberBlogEntity current) {
        return current != null && current.getStatus().equals("ACTIVE")
                && current.getCreatedAt().equals(snapshot.getCreatedAt())
                && current.getBlogUrl().equals(snapshot.getBlogUrl())
                && Objects.equals(current.getRssUrl(), snapshot.getRssUrl())
                && Objects.equals(current.getLastSyncedAt(), snapshot.getLastSyncedAt());
    }

    private List<BlogPostEntity> parseFeed(SafeHttpClient.Result response, String userId, LocalDateTime now)
            throws IOException, FeedException {
        var parser = new SyndFeedInput();
        parser.setAllowDoctypes(false);
        var posts = new ArrayList<BlogPostEntity>();
        try (var reader = new XmlReader(new ByteArrayInputStream(response.body()))) {
            for (var entry : parser.build(reader).getEntries()) {
                if (entry.getLink() == null || entry.getLink().isBlank()) {
                    continue;
                }
                URI link;
                try {
                    link = urls.parse(response.url().resolve(entry.getLink().strip()).toString());
                } catch (IllegalArgumentException | BizException exception) {
                    continue;
                }
                var post = new BlogPostEntity();
                post.setUserId(userId);
                String key = entry.getUri();
                post.setGuidHash(hash(key == null || key.isBlank() ? link.toString() : key));
                String title = Jsoup.parse(entry.getTitle() == null ? "" : entry.getTitle()).text();
                post.setTitle(title.isBlank() ? "无标题" : Text.truncate(title, 255));
                post.setPostUrl(link.toString());
                String description = entry.getDescription() != null ? entry.getDescription().getValue()
                        : entry.getContents().isEmpty() ? "" : entry.getContents().get(0).getValue();
                post.setSummary(Text.truncate(Jsoup.parse(description == null ? "" : description).text(), 300));
                post.setSourceType("RSS");
                var date = entry.getPublishedDate() == null ? entry.getUpdatedDate() : entry.getPublishedDate();
                post.setPublishedAt(date == null ? now : LocalDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC));
                post.setCreatedAt(now);
                posts.add(post);
            }
        }
        posts.sort(Comparator.comparing(BlogPostEntity::getPublishedAt).reversed());
        return posts;
    }

    private static String header(String value) {
        return value != null && value.length() <= 255 ? value : null;
    }

    private static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record SyncRequested(String userId) {}
    private record Notification(MemberBlogEntity author, BlogPostEntity post) {}
}
