package org.lec.blog.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lec.blog.common.exception.BizException;

class SafeUrlValidatorTest {
    private final SafeUrlValidator validator = new SafeUrlValidator();

    @Test
    void acceptsPublicAddress() {
        assertThat(validator.validate("https://93.184.216.34/feed").getPath()).isEqualTo("/feed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///rss.xml", "http://localhost", "http://127.0.0.1", "http://10.0.0.5",
            "http://172.25.4.3", "http://192.168.0.1", "http://169.254.1.1", "http://100.64.0.1",
            "https://mysql.default.svc.cluster.local", "http://[::1]", "http://[fc00::1]", "http://[fe80::1]",
            "http://0.0.0.0", "http://me:pw@93.184.216.34/blog"})
    void rejectsUnsafeUrl(String url) {
        assertThatThrownBy(() -> validator.validate(url)).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsDnsAnswersContainingAnyNonPublicAddress() {
        var mixed = new SafeUrlValidator() {
            @Override
            InetAddress[] resolve(String host) throws UnknownHostException {
                return new InetAddress[]{InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.23.0.5")};
            }
        };
        assertThatThrownBy(() -> mixed.validate("https://blog.example/feed")).isInstanceOf(BizException.class);
    }
}
