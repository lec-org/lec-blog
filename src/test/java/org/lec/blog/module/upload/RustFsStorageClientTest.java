package org.lec.blog.module.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.lec.blog.common.Hashing;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.config.LecBlogProperties;

class RustFsStorageClientTest {
    @Test
    void sendsPathStyleSigV4PutAndReturnsOnlyThePublicObjectAddress() throws Exception {
        try (var server = new MockWebServer()) {
            var client = client(server);
            server.enqueue(new MockResponse().setResponseCode(200));
            byte[] png = HexFormat.of().parseHex("89504e470d0a1a0a00000000");
            var upload = client.upload("alice@example", png, "png", "image/png");
            var request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo("PUT");
            assertThat(request.getPath()).isEqualTo("/rustfs/lec-public/" + upload.key());
            assertThat(upload.key()).matches("blog/phase2/alice_example/\\d{4}/\\d{2}/[a-f0-9-]{36}\\.png");
            assertThat(upload.url()).isEqualTo("https://files.example.org/lec-public/" + upload.key());
            assertThat(upload.size()).isEqualTo(png.length);
            assertThat(request.getBody().readByteArray()).isEqualTo(png);
            assertThat(request.getHeader("Content-Type")).isEqualTo("image/png");
            assertThat(request.getHeader("X-Amz-Content-Sha256")).isEqualTo(Hashing.sha256(png));
            assertThat(request.getHeader("X-Amz-Date")).matches("\\d{8}T\\d{6}Z");
            assertThat(request.getHeader("Authorization"))
                    .matches("AWS4-HMAC-SHA256 Credential=public-key/\\d{8}/us-east-1/s3/aws4_request, "
                            + "SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date, Signature=[a-f0-9]{64}")
                    .doesNotContain("private-secret");
        }
    }

    @Test
    void turnsAStorageFailureIntoAServiceUnavailableResponse() throws Exception {
        try (var server = new MockWebServer()) {
            var client = client(server);
            server.enqueue(new MockResponse().setResponseCode(403).setBody("internal details"));
            assertThatThrownBy(() -> client.upload("alice", new byte[]{1}, "png", "image/png"))
                    .isInstanceOf(BizException.class).hasMessage("图床暂时不可用");
        }
    }

    private static RustFsStorageClient client(MockWebServer server) {
        var storage = new LecBlogProperties.Storage(server.url("/rustfs").toString(), "lec-public", "us-east-1",
                "public-key", "private-secret", "https://files.example.org/lec-public", "blog/phase2");
        return new RustFsStorageClient(new LecBlogProperties(null, null, null, List.of(), "http://localhost:8080", storage));
    }
}
