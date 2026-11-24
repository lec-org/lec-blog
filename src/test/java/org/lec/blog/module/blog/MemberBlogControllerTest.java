package org.lec.blog.module.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lec.blog.auth.AuthUser;
import org.lec.blog.auth.LecSsoTokenVerifier;
import org.lec.blog.module.blog.mapper.MemberBlogMapper;
import org.lec.blog.module.post.service.BlogSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberBlogControllerTest {
    private static final String ME = "/api/v1/blogs/me";
    @Autowired MockMvc mvc;
    @Autowired MemberBlogMapper blogs;
    @MockitoBean LecSsoTokenVerifier verifier;
    @MockitoBean BlogSyncService sync;

    @Test
    void allowsPreflightWithoutGrantingAccessToBlogData() throws Exception {
        String origin = "http://localhost:5173";
        mvc.perform(options(ME).header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "authorization"))
                .andExpect(content().string(""));
        mvc.perform(get(ME).header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bindsListsUpdatesAndUnbindsBlog() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(get(ME).header("X-Dev-User-Id", "crud-member")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(put(ME).header("X-Dev-User-Id", "crud-member").header("X-Dev-Username", "ming")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"blogUrl":"https://93.184.216.34","blogTitle":"Java Notes","bio":"LEC 中文笔记","tags":["Java","K8s"]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.blogTitle").value("Java Notes"))
                .andExpect(jsonPath("$.data.displayName").value("ming")).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("q", "notes").param("tag", "java"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hasRecentPost").value(false));
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("q", "ming"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("tag", "jav"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put(ME).header("X-Dev-User-Id", "crud-member").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blogUrl":"https://93.184.216.34","blogTitle":"Updated","tags":["Spring"]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.blogTitle").value("Updated"));
        mvc.perform(get(ME).header("X-Dev-User-Id", "crud-member").header("X-Dev-Display-Name", "Updated name")
                        .header("X-Dev-Avatar", "https://example.com/new.png"))
                .andExpect(status().isOk());
        await().atMost(Duration.ofSeconds(4)).untilAsserted(() -> {
            assertThat(blogs.selectById("crud-member").getDisplayName()).isEqualTo("Updated name");
            assertThat(blogs.selectById("crud-member").getAvatarUrl()).endsWith("new.png");
        });
        mvc.perform(delete(ME).header("X-Dev-User-Id", "crud-member")).andExpect(status().isOk());
        mvc.perform(get(ME).header("X-Dev-User-Id", "crud-member")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void guardsMemberAndCaptainRoutesAndValidatesInputs() throws Exception {
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(get(ME).header("Authorization", "Basic foo").header("X-Dev-User-Id", "member"))
                .andExpect(status().isUnauthorized());
        when(verifier.verify("visitor")).thenReturn(new AuthUser("visitor", "", "", "", List.of(), List.of(),
                false, false));
        mvc.perform(get(ME).header("Authorization", "Bearer visitor")).andExpect(status().isForbidden());
        mvc.perform(put(ME).header("X-Dev-User-Id", "hidden-owner").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blogUrl":"https://93.184.216.34","blogTitle":"Hidden blog"}
                                """))
                .andExpect(status().isOk());
        String admin = "/api/v1/admin/blogs/hidden-owner";
        mvc.perform(delete(admin).header("X-Dev-User-Id", "regular")).andExpect(status().isForbidden());
        mvc.perform(delete(admin).header("X-Dev-User-Id", "chief").header("X-Dev-Captain", "true"))
                .andExpect(status().isOk());
        mvc.perform(get(ME).header("X-Dev-User-Id", "hidden-owner")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        mvc.perform(delete(ME).header("X-Dev-User-Id", "hidden-owner")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("q", "Hidden blog"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post(admin + "/restore").header("X-Dev-User-Id", "regular")).andExpect(status().isForbidden());
        mvc.perform(post(admin + "/restore").header("X-Dev-User-Id", "chief").header("X-Dev-Captain", "true"))
                .andExpect(status().isOk());
        mvc.perform(get(ME).header("X-Dev-User-Id", "hidden-owner")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/blogs").header("X-Dev-User-Id", "reader").param("q", "Hidden blog"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/posts/recent").header("X-Dev-User-Id", "reader").param("size", "51"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ME).header("X-Dev-User-Id", "hidden-owner").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blogUrl":"http://127.0.0.1"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
