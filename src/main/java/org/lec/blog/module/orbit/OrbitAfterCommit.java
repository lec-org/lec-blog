package org.lec.blog.module.orbit;

import org.lec.blog.module.blog.entity.MemberBlogEntity;
import org.lec.blog.module.post.entity.BlogPostEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrbitAfterCommit {
    private final OrbitEventClient client;

    public OrbitAfterCommit(OrbitEventClient client) {
        this.client = client;
    }

    @TransactionalEventListener
    public void joined(BlogJoined event) {
        client.publishBlogJoined(event.author());
    }

    @TransactionalEventListener
    public void published(PostPublished event) {
        client.publish(event.author(), event.post());
    }

    public record BlogJoined(MemberBlogEntity author) {}
    public record PostPublished(MemberBlogEntity author, BlogPostEntity post) {}
}
