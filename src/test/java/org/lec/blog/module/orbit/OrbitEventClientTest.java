package org.lec.blog.module.orbit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.springframework.web.client.RestClient;

class OrbitEventClientTest {
    @Test
    void sendsJoinedEventsWithACompatibleReadingLink() throws Exception {
        try (var server = new MockWebServer()) {
            var config = new LecBlogProperties(null, new LecBlogProperties.Orbit(server.url("/").toString(), "test-token"),
                    null, List.of(), "http://localhost:8080", null);
            var client = new OrbitEventClient(config, RestClient.builder());
            var author = new MemberBlogEntity();
            author.setUserId("member-01");
            author.setUsername("m01");
            author.setDisplayName("Writer");
            author.setBlogUrl("https://blog.example.org/u/member-01");

            for (String mode : List.of("HOSTED", "EXTERNAL")) {
                server.enqueue(new MockResponse().setResponseCode(204));
                author.setBlogMode(mode);
                author.setBlogTitle(mode.equals("HOSTED") ? "Notes" : "");
                author.setBio(mode.equals("HOSTED") ? "简".repeat(121) : "");
                client.publishBlogJoined(author);

                var request = server.takeRequest(2, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getPath()).isEqualTo("/internal/v1/events");
                assertThat(request.getHeader("X-Orbit-Service-Token")).isEqualTo("test-token");
                var event = new ObjectMapper().readTree(request.getBody().readUtf8());
                assertThat(event.path("sourceApp").asText()).isEqualTo("lec-blog");
                assertThat(event.path("eventType").asText()).isEqualTo("blog.member_joined");
                assertThat(event.path("title").asText()).isEqualTo(mode.equals("HOSTED")
                        ? "Writer 开通了工作室博客《Notes》" : "Writer 绑定了个人博客");
                assertThat(event.path("body").asText()).isEqualTo(mode.equals("HOSTED") ? "简".repeat(120) : author.getBlogUrl());
                assertThat(event.at("/payload/userId").asText()).isEqualTo(author.getUserId());
                assertThat(event.at("/payload/username").asText()).isEqualTo(author.getUsername());
                assertThat(event.at("/payload/blogTitle").asText()).isEqualTo(author.getBlogTitle());
                assertThat(event.at("/payload/blogMode").asText()).isEqualTo(mode);
                assertThat(event.at("/payload/blogUrl").asText()).isEqualTo(author.getBlogUrl());
                assertThat(event.at("/payload/postUrl")).isEqualTo(event.at("/payload/blogUrl"));
            }
        }
    }
}
