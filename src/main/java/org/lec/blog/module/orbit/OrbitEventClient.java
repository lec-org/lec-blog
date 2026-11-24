package org.lec.blog.module.orbit;

import java.net.http.HttpClient;
import java.time.Duration;
import org.lec.blog.common.Text;
import org.lec.blog.config.LecBlogProperties;
import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OrbitEventClient {
    private static final Logger LOG = LoggerFactory.getLogger(OrbitEventClient.class);
    private final String endpoint;
    private final String token;
    private final RestClient http;

    public OrbitEventClient(LecBlogProperties properties, RestClient.Builder builder) {
        var orbit = properties.orbit();
        endpoint = orbit.internalUrl().isBlank() ? "" : orbit.internalUrl().replaceFirst("/+$", "") + "/internal/v1/events";
        token = orbit.internalToken();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        http = builder.requestFactory(factory).build();
    }

    @Async
    public void publish(MemberBlogEntity author, BlogPostEntity post) {
        if (endpoint.isEmpty() || token.isBlank()) {
            return;
        }
        var payload = new Payload(author.getUserId(), author.getUsername(), author.getBlogUrl(),
                post.getPostUrl(), post.getTitle());
        String name = author.getDisplayName().isBlank() ? author.getUsername() : author.getDisplayName();
        if (name.isBlank()) {
            name = author.getBlogTitle().isBlank() ? author.getUserId() : author.getBlogTitle();
        }
        var event = new Event("lec-blog", "blog.post_published", name + " 发布了新博客：《" + post.getTitle() + "》",
                Text.truncate(post.getSummary(), 120), payload);
        try {
            var response = http.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Orbit-Service-Token", token).body(event).retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Orbit 未接受新博文事件，HTTP {}", response.getStatusCode().value());
            }
        } catch (RestClientException | IllegalArgumentException exception) {
            LOG.warn("Orbit 博文事件推送失败，作者 ID {}，错误 {}", author.getUserId(), exception.getClass().getSimpleName());
        }
    }

    private record Event(String sourceApp, String eventType, String title, String body, Payload payload) {}
    private record Payload(String userId, String username, String blogUrl, String postUrl, String postTitle) {}
}
