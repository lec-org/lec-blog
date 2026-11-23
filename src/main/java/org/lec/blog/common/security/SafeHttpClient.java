package org.lec.blog.common.security;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
public class SafeHttpClient {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private final SafeUrlValidator validator;
    private final CloseableHttpClient http;

    public SafeHttpClient(SafeUrlValidator validator) {
        this.validator = validator;
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(10)).build())
                .setDnsResolver(new DnsResolver() {
                    @Override
                    public InetAddress[] resolve(String host) throws UnknownHostException {
                        // The connection uses only addresses checked by this resolver.
                        return validator.resolvePublic(host);
                    }

                    @Override
                    public String resolveCanonicalHostname(String host) {
                        return host;
                    }
                }).build();
        http = HttpClients.custom().setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(10)).build())
                .setUserAgent("LEC-Blog-Bot/1.0").disableRedirectHandling()
                .disableAutomaticRetries().disableCookieManagement().build();
    }

    public Result get(String url, String etag, String lastModified) throws IOException {
        URI target = validator.validate(url);
        for (int redirects = 0; ; redirects++) {
            var request = new HttpGet(target);
            if (etag != null) {
                request.setHeader(HttpHeaders.IF_NONE_MATCH, etag);
            }
            if (lastModified != null) {
                request.setHeader(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
            }
            try (var response = http.executeOpen(null, request, null)) {
                int status = response.getCode();
                if (REDIRECTS.contains(status)) {
                    var location = response.getFirstHeader(HttpHeaders.LOCATION);
                    request.cancel();
                    if (location == null || redirects == MAX_REDIRECTS) {
                        throw new IOException("重定向地址缺失或超过三跳");
                    }
                    try {
                        target = validator.validate(target.resolve(location.getValue()).toString());
                    } catch (IllegalArgumentException exception) {
                        throw new IOException("重定向地址无效", exception);
                    }
                    continue;
                }
                var responseEtag = response.getFirstHeader(HttpHeaders.ETAG);
                var modified = response.getFirstHeader(HttpHeaders.LAST_MODIFIED);
                var type = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                String newEtag = responseEtag == null ? null : responseEtag.getValue();
                String newModified = modified == null ? null : modified.getValue();
                if (status == 304) {
                    return new Result(target, status, new byte[0], newEtag, newModified, null);
                }
                if (status != 200 || response.getEntity() == null) {
                    request.cancel();
                    throw new IOException("目标返回 HTTP " + status);
                }
                byte[] body;
                try {
                    if (response.getEntity().getContentLength() > MAX_BYTES) {
                        throw new IOException("响应内容超过 2 MB");
                    }
                    body = response.getEntity().getContent().readNBytes(MAX_BYTES + 1);
                    if (body.length > MAX_BYTES) {
                        throw new IOException("响应内容超过 2 MB");
                    }
                } catch (IOException exception) {
                    request.cancel();
                    throw exception;
                }
                return new Result(target, status, body, newEtag, newModified, type == null ? null : type.getValue());
            }
        }
    }

    @PreDestroy
    void close() throws IOException {
        http.close();
    }

    public record Result(URI url, int status, byte[] body, String etag, String lastModified, String contentType) {}
}
