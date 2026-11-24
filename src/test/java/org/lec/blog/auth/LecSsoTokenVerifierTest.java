package org.lec.blog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.lec.blog.common.exception.BizException;
import org.lec.blog.config.LecBlogProperties;

class LecSsoTokenVerifierTest {
    @Test
    void verifiesRsaAndTheEs384AlgorithmUsedByLecSso() throws Exception {
        try (var sso = new MockWebServer()) {
            String issuer = sso.url("/oidc").toString();
            var rsa = new RSAKeyGenerator(2048).keyID("rsa").generate();
            var ec = new ECKeyGenerator(Curve.P_384).keyID("lec-ec").generate();
            sso.enqueue(json(new JWKSet(List.of(rsa.toPublicJWK(), ec.toPublicJWK())).toString()));
            var verifier = verifier(issuer);
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa").build(),
                    claims(issuer, 60).claim("username", "ming").claim("organization_id", "lec-studio")
                            .claim("organization_roles", List.of("lec-studio:CAPTAIN")).build());
            jwt.sign(new RSASSASigner(rsa));
            var user = verifier.verify(jwt.serialize());
            assertThat(user.userId()).isEqualTo("member");
            assertThat(user.displayName()).isEqualTo("ming");
            assertThat(user.isStudio()).isTrue();
            assertThat(user.isCaptain()).isTrue();

            var ecJwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES384).keyID("lec-ec").build(),
                    claims(issuer, 60).claim("scope", "openid studio:admin:access").build());
            ecJwt.sign(new ECDSASigner(ec));
            assertThat(verifier.verify(ecJwt.serialize()).isCaptain()).isTrue();

            var slightlyExpired = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa").build(),
                    claims(issuer, -15).build());
            slightlyExpired.sign(new RSASSASigner(rsa));
            assertThat(verifier.verify(slightlyExpired.serialize()).userId()).isEqualTo("member");

            var expired = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa").build(),
                    claims(issuer, -120).build());
            expired.sign(new RSASSASigner(rsa));
            assertThatThrownBy(() -> verifier.verify(expired.serialize())).isInstanceOf(BizException.class);

            var wrongIssuer = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("rsa").build(),
                    claims("https://elsewhere.example/oidc", 60).build());
            wrongIssuer.sign(new RSASSASigner(rsa));
            assertThatThrownBy(() -> verifier.verify(wrongIssuer.serialize())).isInstanceOf(BizException.class);
            assertThat(sso.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/oidc/jwks");
            assertThat(sso.getRequestCount()).isEqualTo(1);
        }
    }

    @Test
    void readsOpaqueUserinfoOnceAndRecognizesNonMembers() throws Exception {
        try (var sso = new MockWebServer()) {
            sso.enqueue(json("""
                    {"sub":"visitor","name":"Guest","organizations":["another-org"],"scope":"openid"}
                    """));
            var verifier = verifier(sso.url("/oidc").toString());
            var user = verifier.verify("opaque-token");
            assertThat(user.isStudio()).isFalse();
            assertThat(user.isCaptain()).isFalse();
            assertThat(verifier.verify("opaque-token")).isEqualTo(user);
            assertThat(sso.getRequestCount()).isEqualTo(1);
            var request = sso.takeRequest(1, TimeUnit.SECONDS);
            assertThat(request.getPath()).isEqualTo("/oidc/me");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer opaque-token");
        }
    }

    private static LecSsoTokenVerifier verifier(String issuer) throws Exception {
        return new LecSsoTokenVerifier(new LecBlogProperties(new LecBlogProperties.Auth(issuer, "lec-studio", false),
                null, null, List.of()), new ObjectMapper());
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static JWTClaimsSet.Builder claims(String issuer, long offset) {
        return new JWTClaimsSet.Builder().issuer(issuer).subject("member")
                .expirationTime(Date.from(Instant.now().plusSeconds(offset)));
    }
}
