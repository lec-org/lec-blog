package org.lec.blog.module.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.lec.blog.auth.AuthUser;
import org.lec.blog.module.blog.dto.EnableHostedBlogRequest;
import org.lec.blog.module.blog.service.MemberBlogService;
import org.lec.blog.module.orbit.OrbitEventClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:hosted;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HostedBlogTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberBlogService blogs;
    @Autowired TransactionTemplate transaction;
    @MockitoBean OrbitEventClient orbit;

    @Test
    void announcesFirstExternalBindingOnlyOnceEvenAfterUnlinking() throws Exception {
        String member = "external-join";
        mvc.perform(external(member, "External")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blogMode").value("EXTERNAL"));
        verify(orbit).publishBlogJoined(argThat(author -> member.equals(author.getUserId())
                && "EXTERNAL".equals(author.getBlogMode())));
        mvc.perform(external(member, "Updated")).andExpect(status().isOk());
        mvc.perform(hosted(member, "external-join")).andExpect(status().isOk());
        mvc.perform(external(member, "Switched back")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostedSlug").value("external-join"));
        mvc.perform(as(delete("/api/v1/blogs/me"), member)).andExpect(status().isOk());
        mvc.perform(external(member, "Rebound")).andExpect(status().isOk());
        verify(orbit, times(1)).publishBlogJoined(any());
    }

    @Test
    void announcesFirstHostedBindingAndNeverAnnouncesARolledBackBinding() throws Exception {
        var member = new AuthUser("hosted-join", "hosted-join", "Writer", "", List.of(), List.of(), true, false);
        transaction.executeWithoutResult(status -> {
            blogs.enableHosted(member, new EnableHostedBlogRequest("joined", "Title", "", List.of()));
            verify(orbit, never()).publishBlogJoined(any());
            status.setRollbackOnly();
        });
        assertThat(blogs.own(member.userId())).isNull();
        verify(orbit, never()).publishBlogJoined(any());

        mvc.perform(hosted(member.userId(), "joined")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blogUrl").value("http://localhost:8080/u/joined"))
                .andExpect(jsonPath("$.data.rssUrl").isEmpty());
        mvc.perform(hosted(member.userId(), "joined")).andExpect(status().isOk());
        mvc.perform(external(member.userId(), "External")).andExpect(status().isOk());
        mvc.perform(hosted(member.userId(), "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hostedSlug").value("joined"));
        verify(orbit, times(1)).publishBlogJoined(argThat(author -> "HOSTED".equals(author.getBlogMode())
                && member.userId().equals(author.getUserId())));
    }

    @Test
    void reservesSlugsAcrossModesAndGeneratesAnAvailableSlug() throws Exception {
        mvc.perform(hosted("slug-admin", "admin")).andExpect(status().isBadRequest());
        mvc.perform(hosted("slug-invalid", "a--b")).andExpect(status().isBadRequest());
        mvc.perform(hosted("slug-first", "same-name")).andExpect(status().isOk());
        mvc.perform(external("slug-first", "External again")).andExpect(status().isOk());
        mvc.perform(hosted("slug-second", "same-name")).andExpect(status().isConflict());
        mvc.perform(hosted("slug-second", "").header("X-Dev-Username", "Same Name"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.hostedSlug").value("same-name-2"));
        mvc.perform(get("/api/v1/public/u/same-name")).andExpect(status().isNotFound());
    }

    @Test
    void protectsDraftsAndOnlyNotifiesOnFirstPublication() throws Exception {
        String member = "draft-writer";
        String slug = "draft-writer";
        String root = "/api/v1/public/u/" + slug;
        mvc.perform(hosted(member, slug)).andExpect(status().isOk());
        long id = createPost(member, "draft-secret", "DRAFT");

        mvc.perform(get(root)).andExpect(status().isOk()).andExpect(jsonPath("$.data.posts.total").value(0));
        mvc.perform(get(root + "/posts/draft-secret")).andExpect(status().isNotFound());
        mvc.perform(get("/u/" + slug + "/p/draft-secret")).andExpect(status().isNotFound());
        mvc.perform(get("/u/" + slug + "/rss.xml")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("draft-secret"))));
        mvc.perform(as(get("/api/v1/blogs/" + member + "/posts"), "reader")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(as(get("/api/v1/blogs/me"), member)).andExpect(jsonPath("$.data.latestPostTitle").isEmpty());
        mvc.perform(as(get("/api/v1/posts/me?status=DRAFT&q=secret"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].contentMd").doesNotExist());
        mvc.perform(as(get("/api/v1/posts/me/" + id), member)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.data.contentMd").exists());
        mvc.perform(as(get("/api/v1/posts/me/" + id), "foreign-reader")).andExpect(status().isNotFound());
        mvc.perform(postBody(put("/api/v1/posts/me/" + id), "foreign-reader", "draft-secret", "PUBLISHED"))
                .andExpect(status().isNotFound());
        verify(orbit, never()).publish(any(), any());

        var published = mvc.perform(postBody(put("/api/v1/posts/me/" + id), member, "draft-secret", "PUBLISHED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary").value("Heading Public bold text."))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty()).andReturn();
        String publishedAt = json.readTree(published.getResponse().getContentAsString()).at("/data/publishedAt").asText();
        mvc.perform(get(root + "/posts/draft-secret")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentMd").value("# Heading\n\nPublic **bold** text."))
                .andExpect(jsonPath("$.data.author.hostedSlug").value(slug));
        mvc.perform(get("/u/" + slug + "/p/draft-secret")).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/u/" + slug + "/rss.xml")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/rss+xml"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("draft-secret")));
        mvc.perform(as(get("/api/v1/blogs/me"), member)).andExpect(jsonPath("$.data.latestPostTitle").value("draft-secret"));

        mvc.perform(postBody(put("/api/v1/posts/me/" + id), member, "draft-secret", "DRAFT")).andExpect(status().isOk());
        mvc.perform(get(root + "/posts/draft-secret")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/blogs/me"), member)).andExpect(jsonPath("$.data.latestPostTitle").isEmpty());
        mvc.perform(postBody(put("/api/v1/posts/me/" + id), member, "draft-secret", "PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").value(publishedAt));
        verify(orbit, times(1)).publish(any(), argThat(post -> post.getId() == id));
    }

    @Test
    void generatesPlainTextSummariesForGfmContent() throws Exception {
        String member = "summary-writer";
        mvc.perform(hosted(member, "summary-writer")).andExpect(status().isOk());
        var body = Map.of("title", "Plain summary", "contentMd", """
                | Header | Value |
                | ------ | ----- |
                | Engine | Java  |

                - [x] ~~Done~~
                """);
        mvc.perform(as(post("/api/v1/posts/me"), member).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("Header Value Engine Java Done"));
    }

    @Test
    void preservesNativePostsAcrossModeChangesAndRewritesLinksOnRename() throws Exception {
        String member = "mode-writer";
        mvc.perform(hosted(member, "old-slug")).andExpect(status().isOk());
        long draft = createPost(member, "retained-draft", "DRAFT");
        long released = createPost(member, "retained-public", "PUBLISHED");

        mvc.perform(external(member, "External mode")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestPostAt").isEmpty());
        mvc.perform(get("/api/v1/public/u/old-slug")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/posts/me/" + draft), member)).andExpect(status().isOk());
        mvc.perform(hosted(member, "new-slug")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestPostUrl").value("http://localhost:8080/u/new-slug/p/retained-public"));
        mvc.perform(as(get("/api/v1/posts/me/" + released), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postUrl").value("http://localhost:8080/u/new-slug/p/retained-public"));
        mvc.perform(get("/api/v1/public/u/new-slug")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts.total").value(1));
    }

    @Test
    void hidingABlogBlocksPrivateArticleAccessUploadsAndPublicRoutes() throws Exception {
        String member = "hidden-writer";
        mvc.perform(hosted(member, "hidden-writer")).andExpect(status().isOk());
        long id = createPost(member, "published", "PUBLISHED");
        mvc.perform(as(delete("/api/v1/admin/blogs/" + member), "captain").header("X-Dev-Captain", "true"))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/posts/me"), member)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/posts/me/" + id), member)).andExpect(status().isForbidden());
        mvc.perform(postBody(post("/api/v1/posts/me"), member, "blocked", "DRAFT")).andExpect(status().isForbidden());
        mvc.perform(postBody(put("/api/v1/posts/me/" + id), member, "published", "DRAFT")).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/posts/me/" + id), member)).andExpect(status().isForbidden());
        mvc.perform(hosted(member, "hidden-writer")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/blogs/me/sync"), member)).andExpect(status().isForbidden());
        mvc.perform(as(multipart("/api/v1/uploads/image").file(new MockMultipartFile("file", new byte[]{1})), member))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/blogs").param("q", member)).andExpect(jsonPath("$.data.length()").value(0));
        for (String route : List.of("/u/hidden-writer", "/u/hidden-writer/rss.xml", "/u/hidden-writer/p/published",
                "/api/v1/public/u/hidden-writer", "/api/v1/public/u/hidden-writer/posts/published")) {
            mvc.perform(get(route)).andExpect(status().isNotFound());
        }
    }

    @Test
    void uploadRejectsAnonymousSvgAndOversizeAndSupportsDisabledStorage() throws Exception {
        var png = new MockMultipartFile("file", "image.png", "image/png",
                java.util.HexFormat.of().parseHex("89504e470d0a1a0a00000000"));
        var svg = new MockMultipartFile("file", "pretend.png", "image/png", "<svg/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/uploads/image").file(png)).andExpect(status().isUnauthorized());
        mvc.perform(as(multipart("/api/v1/uploads/image").file(svg), "uploader")).andExpect(status().isBadRequest());
        mvc.perform(as(multipart("/api/v1/uploads/image")
                .file(new MockMultipartFile("file", new byte[10 * 1024 * 1024 + 1])), "uploader"))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(as(multipart("/api/v1/uploads/image").file(png), "uploader")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/public/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mockEnabled").value(true))
                .andExpect(jsonPath("$.data.imageUploadEnabled").value(false))
                .andExpect(jsonPath("$.data.secretKey").doesNotExist());
        mvc.perform(get("/api/v1/posts/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/callback")).andExpect(forwardedUrl("/index.html"));
    }

    private MockHttpServletRequestBuilder hosted(String user, String slug) throws Exception {
        return as(post("/api/v1/blogs/me/enable-hosted"), user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("hostedSlug", slug, "blogTitle", user)));
    }

    private MockHttpServletRequestBuilder external(String user, String title) throws Exception {
        return as(put("/api/v1/blogs/me"), user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("blogUrl", "https://93.184.216.34/", "blogTitle", title)));
    }

    private long createPost(String user, String slug, String status) throws Exception {
        var result = mvc.perform(postBody(post("/api/v1/posts/me"), user, slug, status))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }

    private MockHttpServletRequestBuilder postBody(MockHttpServletRequestBuilder request, String user,
                                                    String slug, String status) throws Exception {
        return as(request, user).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                "title", slug, "slug", slug, "postStatus", status, "contentMd", "# Heading\n\nPublic **bold** text.")));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String user) {
        return request.header("X-Dev-User-Id", user);
    }
}
