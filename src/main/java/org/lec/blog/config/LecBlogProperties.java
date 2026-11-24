package org.lec.blog.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lec")
public record LecBlogProperties(Auth auth, Orbit orbit, Rss rss, List<String> corsAllowedOrigins,
                                String publicUrl, Storage storage) {
    public LecBlogProperties {
        publicUrl = publicUrl.strip().replaceFirst("/+$", "");
        var uri = URI.create(publicUrl);
        if (uri.getHost() == null || !List.of("https", "http").contains(uri.getScheme())
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || publicUrl.length() > 340) {
            throw new IllegalArgumentException("BLOG_PUBLIC_URL 必须是有效的 HTTP 或 HTTPS 基础地址");
        }
    }

    public String hostedUrl(String slug) {
        return publicUrl + "/u/" + slug;
    }

    public record Auth(String issuer, String orgId, boolean mockEnabled, String webClientId) {}
    public record Orbit(String internalUrl, String internalToken) {}
    public record Rss(boolean syncEnabled, long intervalMs) {}
    public record Storage(String endpoint, String bucket, String region, String accessKey, String secretKey,
                          String publicUrl, String keyPrefix) {
        public boolean enabled() {
            return !endpoint.isBlank() && !accessKey.isBlank() && !secretKey.isBlank() && !publicUrl.isBlank();
        }
    }
}
