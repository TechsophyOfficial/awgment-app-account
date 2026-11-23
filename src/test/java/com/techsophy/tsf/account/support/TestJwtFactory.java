package com.techsophy.tsf.account.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory RS256 JWK token factory for fast Layer-1 tests without Docker/Keycloak.
 */
public final class TestJwtFactory {

    private final RSAKey rsaKey;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public TestJwtFactory() {
        KeyPair keyPair = generateRsaKeyPair();
        this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        this.decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    public String mintToken(TokenSpec spec) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(spec.issuer())
                .subject(spec.subject())
                .audience(spec.audience())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(spec.expiresInSeconds()))
                .claim("azp", spec.authorizedParty())
                .claim("preferred_username", spec.preferredUsername());

        if (spec.email() != null) {
            builder.claim("email", spec.email());
        }
        if (spec.emailVerified() != null) {
            builder.claim("email_verified", spec.emailVerified());
        }
        if (spec.identityProvider() != null) {
            builder.claim("identity_provider", spec.identityProvider());
        }
        if (spec.idpTenantId() != null) {
            builder.claim("idp_tenant_id", spec.idpTenantId());
        }
        if (spec.awgmentStatus() != null) {
            builder.claim("awgment_status", spec.awgmentStatus());
        }
        if (spec.clientId() != null) {
            builder.claim("client_id", spec.clientId());
        }
        if (spec.tokenKind() != null) {
            builder.claim("token_kind", spec.tokenKind());
        }
        if (spec.resourceAccess() != null && !spec.resourceAccess().isEmpty()) {
            builder.claim("resource_access", spec.resourceAccess());
        }
        if (spec.groups() != null && !spec.groups().isEmpty()) {
            builder.claim("groups", spec.groups());
        }

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(rsaKey.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
    }

    public Jwt decode(String tokenValue) {
        return decoder.decode(tokenValue);
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key generation failed", ex);
        }
    }

    public record TokenSpec(
            String issuer,
            String subject,
            List<String> audience,
            String authorizedParty,
            String preferredUsername,
            String email,
            Boolean emailVerified,
            String identityProvider,
            String idpTenantId,
            String awgmentStatus,
            String clientId,
            String tokenKind,
            Map<String, Map<String, List<String>>> resourceAccess,
            List<String> groups,
            long expiresInSeconds
    ) {
        public static TokenSpec activeHuman(String realm, String sub, String username, List<String> awgmentRoles) {
            return new TokenSpec(
                    "http://localhost:8080/realms/" + realm,
                    sub,
                    List.of("awgment-account"),
                    "awgment-web",
                    username,
                    null,
                    true,
                    null,
                    null,
                    "ACTIVE",
                    null,
                    null,
                    Map.of("awgment-account", Map.of("roles", awgmentRoles)),
                    List.of(),
                    300
            );
        }

        public static TokenSpec serviceAccount(String realm, String clientId, List<String> awgmentRoles) {
            return new TokenSpec(
                    "http://localhost:8080/realms/" + realm,
                    "svc-" + clientId,
                    List.of("awgment-account"),
                    clientId,
                    "service-account-" + clientId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    clientId,
                    "service_account",
                    Map.of("awgment-account", Map.of("roles", awgmentRoles)),
                    List.of(),
                    300
            );
        }
    }
}
