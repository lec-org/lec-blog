package org.lec.blog.module.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.security.SafeHttpClient;
import org.lec.blog.module.blog.dto.UpsertMyBlogRequest;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.lec.blog.module.post.mapper.BlogPostMapper;
import org.lec.blog.module.post.service.BlogSyncService;
import org.lec.blog.module.post.service.RssDiscoveryService;
import org.lec.blog.auth.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlogSyncServiceTest {
    private static final MockWebServer ORBIT = new MockWebServer();
    private static final String HOME = "https://93.184.216.34/";
    private static final String FEED = HOME + "rss";
    @Autowired BlogSyncService sync;
    @Autowired RssDiscoveryService discovery;
    @Autowired MemberBlogService members;
    @Autowired MemberBlogMapper blogs;
    @Autowired BlogPostMapper posts;
    @Autowired MockMvc mvc;
    @MockitoBean SafeHttpClient http;

    @DynamicPropertySource
    static void orbit(DynamicPropertyRegistry registry) throws Exception {
        ORBIT.start();
        registry.add("lec.orbit.internal-url", () -> ORBIT.url("/").toString());
        registry.add("lec.orbit.internal-token", () -> "test-service-token");
    }

    @AfterAll
    static void stopOrbit() throws Exception {
        ORBIT.close();
    }

    @Test
    void discoversFeedImportsOnceAndNotifiesOnlyOnANewRecentPost() throws Exception {
        when(http.get(HOME, null, null)).thenReturn(result(HOME, 200, """
                <html><head><link rel="alternate" type="application/atom+xml" href="/rss"></head></html>
                """, null, null));
        assertThat(discovery.discover(HOME)).contains(FEED);
        String first = item("one", "First article", -2);
        String old = item("old", "Older article", -8);
        String current = item("two", "Second article", -1);
        when(http.get(FEED, null, null)).thenReturn(result(FEED, 200, rss(first + first), "v1", "t1"));
        when(http.get(FEED, "v1", "t1")).thenReturn(result(FEED, 200, rss(first + old + current), "v2", "t2"));
        when(http.get(FEED, "v2", "t2")).thenReturn(result(FEED, 304, "", null, null));
        members.upsert(new AuthUser("feed-author", "ming", "Ming", "https://example.com/avatar", java.util.List.of(),
                java.util.List.of(), true, false), new UpsertMyBlogRequest(HOME, "My Blog", "", null, null, true));

        sync.sync("feed-author");
        assertThat(blogs.selectById("feed-author").isRssInitialized()).isTrue();
        assertThat(postCount("feed-author")).isEqualTo(1);
        assertThat(ORBIT.takeRequest(250, TimeUnit.MILLISECONDS)).isNull();

        ORBIT.enqueue(new MockResponse().setResponseCode(201).setBody("{}"));
        sync.sync("feed-author");
        assertThat(postCount("feed-author")).isEqualTo(3);
        assertThat(blogs.selectById("feed-author").getLastPostTitle()).isEqualTo("Second article");
        var event = ORBIT.takeRequest(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.getPath()).isEqualTo("/internal/v1/events");
        assertThat(event.getMethod()).isEqualTo("POST");
        assertThat(event.getHeader("X-Orbit-Service-Token")).isEqualTo("test-service-token");
        var body = new ObjectMapper().readTree(event.getBody().readUtf8());
        assertThat(body.path("sourceApp").asText()).isEqualTo("lec-blog");
        assertThat(body.path("eventType").asText()).isEqualTo("blog.post_published");
        assertThat(body.path("payload").path("postTitle").asText()).isEqualTo("Second article");
        assertThat(body.path("body").asText()).isEqualTo("Summary");
        sync.sync("feed-author");
        assertThat(postCount("feed-author")).isEqualTo(3);
        assertThat(blogs.selectById("feed-author").getRssEtag()).isEqualTo("v2");
        assertThat(ORBIT.getRequestCount()).isEqualTo(1);

        mvc.perform(get("/api/v1/posts/recent").header("X-Dev-User-Id", "reader").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.items[0].displayName").value("Ming"))
                .andExpect(jsonPath("$.data.items[0].title").value("Second article"));
        mvc.perform(get("/api/v1/blogs/feed-author/posts").header("X-Dev-User-Id", "reader").param("page", "2").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].title").value("First article"));
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("q", "Ming"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].hasRecentPost").value(true));

        members.hide("feed-author");
        mvc.perform(get("/api/v1/posts/recent").header("X-Dev-User-Id", "reader"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertThatThrownBy(() -> members.delete("feed-author")).isInstanceOf(BizException.class);
        members.restore("feed-author");
        members.delete("feed-author");
        assertThat(postCount("feed-author")).isZero();
    }

    @Test
    void limitsManualSyncRequestsPerUser() throws Exception {
        when(http.get(HOME + "manual", null, null))
                .thenReturn(result(HOME + "manual", 200, rss(""), null, null));
        members.upsert(new AuthUser("manual-author", "", "", "", java.util.List.of(), java.util.List.of(), true, false),
                new UpsertMyBlogRequest(HOME, "", "", null, HOME + "manual", false));
        sync.requestManual("manual-author");
        assertThatThrownBy(() -> sync.requestManual("manual-author")).isInstanceOf(BizException.class)
                .hasMessageContaining("5 分钟");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> blogs.selectById("manual-author").isRssInitialized());
    }

    @Test
    void capsImportedPostsToFiftyAndContinuesSchedulerAfterUnexpectedFailure() throws Exception {
        var manyItems = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            manyItems.append(item("bulk-" + i, "Bulk " + i, -i));
        }
        when(http.get(HOME + "bulk-feed", null, null))
                .thenReturn(result(HOME + "bulk-feed", 200, rss(manyItems.toString()), null, null));
        members.upsert(new AuthUser("bulk-author", "bulk", "Bulk Author", "", java.util.List.of(),
                java.util.List.of(), true, false), new UpsertMyBlogRequest(HOME, "Bulk Blog", "", null, HOME + "bulk-feed", false));
        sync.sync("bulk-author");
        assertThat(postCount("bulk-author")).isEqualTo(50);

        when(http.get(HOME + "boom-feed", null, null)).thenThrow(new IllegalStateException("malformed parser crash"));
        members.upsert(new AuthUser("boom-author", "boom", "Boom Author", "", java.util.List.of(),
                java.util.List.of(), true, false), new UpsertMyBlogRequest(HOME, "Boom Blog", "", null, HOME + "boom-feed", false));
        sync.sync("boom-author");
        assertThat(blogs.selectById("boom-author").getSyncError()).isEqualTo("订阅源解析异常");

        members.delete("bulk-author");
        members.delete("boom-author");
    }

    private long postCount(String author) {
        return posts.selectCount(Wrappers.<BlogPostEntity>lambdaQuery().eq(BlogPostEntity::getUserId, author));
    }

    private static SafeHttpClient.Result result(String url, int status, String body, String etag, String modified) {
        return new SafeHttpClient.Result(URI.create(url), status, body.getBytes(StandardCharsets.UTF_8), etag, modified, null);
    }

    private static String rss(String items) {
        return "<rss version='2.0'><channel><title>Blog</title><link>" + HOME + "</link><description>Test blog</description>"
                + items + "</channel></rss>";
    }

    private static String item(String id, String title, int days) {
        String date = ZonedDateTime.now().plusDays(days).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        return """
                <item><guid isPermaLink="false">%s</guid><title>%s</title><link>%s</link>
                <description><![CDATA[<p>Summary</p>]]></description><pubDate>%s</pubDate></item>
                """.formatted(id, title, HOME + id, date);
    }
}
