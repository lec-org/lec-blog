package org.lec.blog.module.upload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.lec.blog.common.Hashing;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.config.LecBlogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RustFsStorageClient {
    private static final Logger LOG = LoggerFactory.getLogger(RustFsStorageClient.class);
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    private static final String SIGNED_HEADERS = "content-type;host;x-amz-content-sha256;x-amz-date";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'", Locale.ROOT);
    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("uuuu/MM", Locale.ROOT);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();
    private final LecBlogProperties.Storage storage;
    private final String endpoint;
    private final String publicUrl;
    private final String prefix;

    public RustFsStorageClient(LecBlogProperties properties) {
        storage = properties.storage();
        if (storage.enabled()) {
            endpoint = baseUrl(storage.endpoint());
            publicUrl = baseUrl(storage.publicUrl());
            prefix = storage.keyPrefix().strip().replaceAll("^/+|/+$", "");
            if (!storage.bucket().matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
                    || !storage.region().matches("[a-z0-9-]+")
                    || Arrays.stream(prefix.split("/")).anyMatch(part -> !part.matches("[a-zA-Z0-9_-]+"))
                    || publicUrl.length() + prefix.length() > 315) {
                throw new IllegalArgumentException("LEC_S3 桶、区域、对象前缀或公开地址配置无效");
            }
        } else {
            endpoint = publicUrl = prefix = "";
        }
    }

    public ImageUpload upload(String userId, byte[] bytes, String extension, String contentType) {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        String safeUserId = userId.replaceAll("[^a-zA-Z0-9_-]", "_");
        String key = prefix + "/" + safeUserId + "/" + KEY_DATE.format(now) + "/" + UUID.randomUUID() + "." + extension;
        URI uri = URI.create(endpoint + "/" + storage.bucket() + "/" + key);
        String hash = Hashing.sha256(bytes);
        String date = AMZ_DATE.format(now);
        String day = date.substring(0, 8);
        String scope = day + "/" + storage.region() + "/s3/aws4_request";
        String headers = "content-type:" + contentType + "\nhost:" + host(uri)
                + "\nx-amz-content-sha256:" + hash + "\nx-amz-date:" + date + "\n";
        String request = String.join("\n", "PUT", uri.getRawPath(), "", headers, SIGNED_HEADERS, hash);
        String toSign = String.join("\n", ALGORITHM, date, scope, Hashing.sha256(request));
        byte[] dayKey = hmac(("AWS4" + storage.secretKey()).getBytes(StandardCharsets.UTF_8), day);
        byte[] regionKey = hmac(dayKey, storage.region());
        byte[] signingKey = hmac(hmac(regionKey, "s3"), "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(signingKey, toSign));
        var put = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("Content-Type", contentType).header("X-Amz-Content-Sha256", hash)
                .header("X-Amz-Date", date)
                .header("Authorization", ALGORITHM + " Credential=" + storage.accessKey() + "/" + scope
                        + ", SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + signature)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        try {
            var response = http.send(put, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new ImageUpload(publicUrl + "/" + key, key, bytes.length, contentType);
            }
            LOG.warn("图床拒绝上传，HTTP {}", response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            LOG.warn("图床上传失败，{}", exception.getClass().getSimpleName());
        }
        throw new BizException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private static String baseUrl(String value) {
        var uri = URI.create(value.strip());
        if (uri.getHost() == null || (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("LEC_S3 地址必须为 HTTP 或 HTTPS 基础地址");
        }
        return uri.getScheme() + "://" + host(uri) + uri.getRawPath().replaceFirst("/+$", "");
    }

    private static String host(URI uri) {
        int port = uri.getPort();
        String name = uri.getHost().toLowerCase(Locale.ROOT);
        return port == -1 || port == ("https".equals(uri.getScheme()) ? 443 : 80) ? name : name + ":" + port;
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 unavailable", exception);
        }
    }

    public record ImageUpload(String url, String key, long size, String contentType) {}
}
