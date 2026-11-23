package org.lec.blog.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.lec.blog.common.Text;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.common.exception.ErrorCode;
import org.lec.blog.config.LecBlogProperties;
import org.springframework.stereotype.Component;

@Component
public class LecSsoTokenVerifier {
    private static final int USERINFO_LIMIT = 64 * 1024;
    private static final Set<String> CAPTAIN_ROLES = Set.of("captain", "admin", "lec-captain");
    private final DefaultJWTProcessor<SecurityContext> jwt = new DefaultJWTProcessor<>();
    private final Cache<String, AuthUser> opaque = Caffeine.newBuilder()
            .maximumSize(2000).expireAfterWrite(Duration.ofSeconds(60)).build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI userinfo;
    private final String orgId;
    private final ObjectMapper json;

    public LecSsoTokenVerifier(LecBlogProperties config, ObjectMapper json) throws IOException {
        String issuer = config.auth().issuer().replaceFirst("/+$", "");
        this.orgId = config.auth().orgId();
        this.json = json;
        this.userinfo = URI.create(issuer + "/me");
        var keys = JWKSourceBuilder.<SecurityContext>create(URI.create(issuer + "/jwks").toURL(),
                new DefaultResourceRetriever(5000, 5000, USERINFO_LIMIT)).build();
        jwt.setJWSKeySelector(new JWSVerificationKeySelector<>(Set.of(
                JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
                JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512), keys));
        var claimsVerifier = new DefaultJWTClaimsVerifier<SecurityContext>(
                new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("sub", "exp"));
        claimsVerifier.setMaxClockSkew(0);
        jwt.setJWTClaimsSetVerifier(claimsVerifier);
    }

    public AuthUser verify(String token) {
        try {
            if (token.chars().filter(character -> character == '.').count() == 2) {
                return toUser(jwt.process(token, null).getClaims());
            }
            return opaque.get(sha256(token), ignored -> fetchUserinfo(token));
        } catch (ParseException | JOSEException | com.nimbusds.jose.proc.BadJOSEException exception) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private AuthUser fetchUserinfo(String token) {
        var request = HttpRequest.newBuilder(userinfo).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json").build();
        try {
            // The trusted SSO endpoint returns a small JSON document.
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200 || response.body().length > USERINFO_LIMIT) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            return toUser(json.readValue(response.body(), new TypeReference<Map<String, Object>>() {}));
        } catch (IOException exception) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private AuthUser toUser(Map<String, Object> claims) {
        if (claims == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String userId = string(claims, "sub");
        if (userId.isBlank() || userId.length() > 128) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String username = Text.truncate(string(claims, "username"), 128);
        String name = Text.truncate(string(claims, "name"), 128);
        var organizations = new ArrayList<>(strings(claims.get("organizations")));
        String org = string(claims, "organization_id");
        if (!org.isEmpty() && !organizations.contains(org)) {
            organizations.add(org);
        }
        var roles = strings(claims.get("organization_roles"));
        boolean captain = roles.stream().map(role -> role.substring(role.lastIndexOf(':') + 1))
                .anyMatch(role -> CAPTAIN_ROLES.contains(role.strip().toLowerCase(Locale.ROOT)))
                || Arrays.asList(string(claims, "scope").split("\\s+")).contains("studio:admin:access");
        return new AuthUser(userId, username, name.isBlank() ? username : name,
                Text.truncate(string(claims, "picture"), 512), List.copyOf(organizations), roles,
                organizations.contains(orgId), captain);
    }

    private static String string(Map<String, Object> claims, String name) {
        return claims.get(name) instanceof String value ? value : "";
    }

    private static List<String> strings(Object claim) {
        if (claim instanceof List<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
