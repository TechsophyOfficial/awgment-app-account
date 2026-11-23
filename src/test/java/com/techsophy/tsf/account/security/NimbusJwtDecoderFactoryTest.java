package com.techsophy.tsf.account.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NimbusJwtDecoderFactoryTest {

    private final NimbusJwtDecoderFactory factory = new NimbusJwtDecoderFactory();
    private HttpServer oidcServer;
    private String issuerUri;
    private RSAKey rsaKey;
    private JwtEncoder encoder;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

        oidcServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = oidcServer.getAddress().getPort();
        issuerUri = "http://localhost:" + port + "/realms/acme";
        String jwksUri = issuerUri + "/protocol/openid-connect/certs";
        String discoveryJson = """
                {
                  "issuer": "%s",
                  "jwks_uri": "%s",
                  "id_token_signing_alg_values_supported": ["RS256"]
                }
                """.formatted(issuerUri, jwksUri);
        String jwksJson = new JWKSet(rsaKey.toPublicJWK()).toString();

        oidcServer.createContext("/realms/acme/.well-known/openid-configuration", exchange -> {
            byte[] bytes = discoveryJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        oidcServer.createContext("/realms/acme/protocol/openid-connect/certs", exchange -> {
            byte[] bytes = jwksJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        oidcServer.start();
    }

    @AfterEach
    void tearDown() {
        if (oidcServer != null) {
            oidcServer.stop(0);
        }
    }

    @Test
    void createShouldValidateArguments() {
        assertThatThrownBy(() -> factory.create(null, "awgment-account"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create("  ", "awgment-account"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create("http://localhost:8080/realms/acme", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create("http://localhost:8080/realms/acme", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDecodeValidTokenAndEnforceIssuerAudienceExpiryAndSignature() throws Exception {
        JwtDecoder decoder = factory.create(issuerUri, "awgment-account");

        String valid = mint(encoder, rsaKey.getKeyID(), issuerUri, List.of("awgment-account"), 300);
        Jwt decoded = decoder.decode(valid);
        assertThat(decoded.getSubject()).isEqualTo("sub-1");
        assertThat(decoded.getAudience()).contains("awgment-account");

        String wrongAudience = mint(encoder, rsaKey.getKeyID(), issuerUri, List.of("other-client"), 300);
        assertThatThrownBy(() -> decoder.decode(wrongAudience))
                .isInstanceOf(JwtValidationException.class);

        String wrongIssuer = mint(encoder, rsaKey.getKeyID(), "http://localhost:8080/realms/other",
                List.of("awgment-account"), 300);
        assertThatThrownBy(() -> decoder.decode(wrongIssuer))
                .isInstanceOf(JwtValidationException.class);

        String expired = mint(encoder, rsaKey.getKeyID(), issuerUri, List.of("awgment-account"), -120);
        assertThatThrownBy(() -> decoder.decode(expired))
                .isInstanceOf(JwtValidationException.class);

        KeyPairGenerator otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        KeyPair otherPair = otherGen.generateKeyPair();
        RSAKey otherKey = new RSAKey.Builder((RSAPublicKey) otherPair.getPublic())
                .privateKey((RSAPrivateKey) otherPair.getPrivate())
                .keyID(rsaKey.getKeyID())
                .build();
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
        String wrongKeyToken = mint(otherEncoder, otherKey.getKeyID(), issuerUri, List.of("awgment-account"), 300);
        assertThatThrownBy(() -> decoder.decode(wrongKeyToken))
                .isInstanceOf(JwtException.class);
    }

    private static String mint(
            JwtEncoder jwtEncoder, String keyId, String iss, List<String> aud, long expiresInSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expiresInSeconds);
        Instant iat = expiresInSeconds > 0 ? now : exp.minusSeconds(60);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(iss)
                .subject("sub-1")
                .audience(aud)
                .issuedAt(iat)
                .expiresAt(exp)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
