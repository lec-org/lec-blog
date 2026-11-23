package org.lec.blog.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lec")
public record LecBlogProperties(Auth auth, Orbit orbit, Rss rss, List<String> corsAllowedOrigins) {
    public record Auth(String issuer, String orgId, boolean mockEnabled) {}
    public record Orbit(String internalUrl, String internalToken) {}
    public record Rss(boolean syncEnabled, long intervalMs) {}
}
