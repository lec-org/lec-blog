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
        var payload = new Payload(author.getUserId(), author.getUsername(), author.getBlogUrl(),
                post.getPostUrl(), post.getTitle());
        String name = memberName(author);
        if (name.isBlank()) {
            name = author.getBlogTitle().isBlank() ? author.getUserId() : author.getBlogTitle();
        }
        send(author.getUserId(), new Event("lec-blog", "blog.post_published",
                name + " 发布了新博客：《" + post.getTitle() + "》",
                Text.truncate(post.getSummary(), 120), payload));
    }

    @Async
    public void publishBlogJoined(MemberBlogEntity author) {
        String name = memberName(author);
        String title = (name.isBlank() ? author.getUserId() : name) + ("HOSTED".equals(author.getBlogMode())
                ? " 开通了工作室博客" : " 绑定了个人博客");
        if (!author.getBlogTitle().isBlank()) {
            title += "《" + author.getBlogTitle() + "》";
        }
        String body = author.getBio().isBlank() ? author.getBlogUrl() : Text.truncate(author.getBio(), 120);
        var payload = new JoinedPayload(author.getUserId(), author.getUsername(), author.getBlogUrl(),
                author.getBlogUrl(), author.getBlogTitle(), author.getBlogMode());
        send(author.getUserId(), new Event("lec-blog", "blog.member_joined", title, body, payload));
    }

    private void send(String userId, Event event) {
        if (endpoint.isEmpty() || token.isBlank()) {
            return;
        }
        try {
            var response = http.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .header("X-Orbit-Service-Token", token).body(event).retrieve().toBodilessEntity();
            if (!response.getStatusCode().is2xxSuccessful()) {
                LOG.warn("Orbit 未接受博客事件 {}，HTTP {}", event.eventType(), response.getStatusCode().value());
            }
        } catch (RestClientException | IllegalArgumentException exception) {
            LOG.warn("Orbit 博客事件 {} 推送失败，作者 ID {}，错误 {}",
                    event.eventType(), userId, exception.getClass().getSimpleName());
        }
    }

    private static String memberName(MemberBlogEntity author) {
        return author.getDisplayName().isBlank() ? author.getUsername() : author.getDisplayName();
    }

    private record Event(String sourceApp, String eventType, String title, String body, Object payload) {}
    private record Payload(String userId, String username, String blogUrl, String postUrl, String postTitle) {}
    private record JoinedPayload(String userId, String username, String blogUrl, String postUrl,
                                 String blogTitle, String blogMode) {}
}
