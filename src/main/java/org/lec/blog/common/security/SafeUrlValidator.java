package org.lec.blog.common.security;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SafeUrlValidator {
    public URI validate(String value) {
        URI uri = parse(value);
        try {
            resolvePublic(uri.getHost());
            return uri;
        } catch (UnknownHostException exception) {
            throw invalidUrl();
        }
    }

    public URI parse(String value) {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw invalidUrl();
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalidUrl();
        }
        if ((!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null
                || uri.getPort() == 0 || uri.getPort() > 65535 || internalHost(uri.getHost())) {
            throw invalidUrl();
        }
        return uri;
    }

    InetAddress[] resolvePublic(String host) throws UnknownHostException {
        if (internalHost(host)) {
            throw new UnknownHostException("Internal hostname");
        }
        InetAddress[] addresses = resolve(host);
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new UnknownHostException("Non-public address");
            }
        }
        return addresses;
    }

    InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    private static boolean internalHost(String value) {
        String host = value.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return host.indexOf('%') >= 0
                || (!host.contains(".") && !host.contains(":"))
                || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".localdomain")
                || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home.arpa");
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        if (bytes.length == 4) {
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        // Global unicast only. Exclude transition, special-purpose and documentation networks.
        return (first & 0xe0) == 0x20 && !(first == 0x20 && second == 0x02)
                && !(first == 0x20 && second == 0x01 && (third < 2 || (third == 0x0d && bytes[3] == (byte) 0xb8)));
    }

    private static BizException invalidUrl() {
        return new BizException(ErrorCode.BAD_REQUEST, "URL 必须指向可解析的公网 HTTP 或 HTTPS 地址");
    }
}
