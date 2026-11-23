package com.techsophy.tsf.account.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.error.ApiErrorCode;
import com.techsophy.tsf.account.tenant.TenantIdValidator;
import com.techsophy.tsf.account.tenant.TenantLookupService;
import com.techsophy.tsf.account.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-tenant {@link AuthenticationManagerResolver} enforcing:
 * <ol>
 *   <li>Strict unverified JWT structure &amp; {@code iss} prefix validation (SSRF prevention before any network call).</li>
 *   <li>Normalized path-traversal/encoding-safe Platform Control Realm isolation for {@code /accounts/v2/tenants/**}.</li>
 *   <li>Active-tenant readiness gating via {@link TenantLookupService} before creating/resolving JWKS decoders.</li>
 *   <li>Bounded LRU + TTL per-issuer {@link JwtAuthenticationProvider} cache with lock-free single-flight builds.</li>
 * </ol>
 */
@Component
public class TenantAuthenticationManagerResolver implements AuthenticationManagerResolver<HttpServletRequest> {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PLATFORM_TENANTS_PATH = "/accounts/v2/tenants";
    private static final String PLATFORM_TENANTS_SUBPATH_PREFIX = "/accounts/v2/tenants/";
    private static final int MAX_JWT_LENGTH = 16 * 1024;
    private static final UrlPathHelper URL_PATH_HELPER = createUrlPathHelper();

    private final AwgmentSecurityProperties properties;
    private final TenantLookupService tenantLookupService;
    private final JwtDecoderFactory jwtDecoderFactory;
    private final JwtRoleConverter jwtRoleConverter;
    private final ObjectMapper objectMapper;
    private final KeycloakIssuerParser issuerParser;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Object cacheLock = new Object();
    private final Map<String, CachedManager> authenticationManagers;
    private final ConcurrentHashMap<String, CompletableFuture<AuthenticationManager>> inFlightBuilds =
            new ConcurrentHashMap<>();
    private final AtomicLong globalEvictEpoch = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> issuerEvictEpochs = new ConcurrentHashMap<>();

    @Autowired
    public TenantAuthenticationManagerResolver(
            AwgmentSecurityProperties properties,
            TenantLookupService tenantLookupService,
            JwtDecoderFactory jwtDecoderFactory,
            JwtRoleConverter jwtRoleConverter,
            ObjectMapper objectMapper) {
        this(properties, tenantLookupService, jwtDecoderFactory, jwtRoleConverter, objectMapper, Clock.systemUTC());
    }

    TenantAuthenticationManagerResolver(
            AwgmentSecurityProperties properties,
            TenantLookupService tenantLookupService,
            JwtDecoderFactory jwtDecoderFactory,
            JwtRoleConverter jwtRoleConverter,
            ObjectMapper objectMapper,
            Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.tenantLookupService = Objects.requireNonNull(tenantLookupService, "tenantLookupService must not be null");
        this.jwtDecoderFactory = Objects.requireNonNull(jwtDecoderFactory, "jwtDecoderFactory must not be null");
        this.jwtRoleConverter = Objects.requireNonNull(jwtRoleConverter, "jwtRoleConverter must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.issuerParser = new KeycloakIssuerParser(this.properties);

        int maxCacheSize = Math.max(1, this.properties.getDecoderCacheMaxSize());
        this.cacheTtl = Duration.ofSeconds(Math.max(0L, this.properties.getDecoderCacheTtlSeconds()));
        this.authenticationManagers = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedManager> eldest) {
                return size() > maxCacheSize;
            }
        };
    }

    private static UrlPathHelper createUrlPathHelper() {
        UrlPathHelper helper = new UrlPathHelper();
        helper.setUrlDecode(true);
        helper.setRemoveSemicolonContent(true);
        return helper;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        String token = extractBearerToken(request);
        String unverifiedIssuer = extractUnverifiedIssuer(token);
        String realm = issuerParser.extractRealm(unverifiedIssuer)
                .orElseThrow(() -> new InvalidBearerTokenException("JWT issuer is not a trusted Keycloak realm."));

        validateRouteAndTenantStatus(request, realm);
        String canonicalIssuerUri = issuerParser.issuerUriForRealm(realm);
        return resolveOrBuildManager(canonicalIssuerUri);
    }

    public void evictIssuerCache(String issuerUri) {
        if (issuerUri != null && !issuerUri.isBlank()) {
            String normalized = issuerUri.trim();
            synchronized (cacheLock) {
                issuerEvictEpochs.computeIfAbsent(normalized, k -> new AtomicLong()).incrementAndGet();
                authenticationManagers.remove(normalized);
            }
        }
    }

    public void evictTenant(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            evictIssuerCache(properties.normalizedKeycloakBaseUrl() + "/realms/" + tenantId.trim());
        }
    }

    public void evictAll() {
        synchronized (cacheLock) {
            globalEvictEpoch.incrementAndGet();
            authenticationManagers.clear();
        }
    }

    private AuthenticationManager resolveOrBuildManager(String canonicalIssuerUri) {
        Instant now = clock.instant();
        if (!cacheTtl.isZero()) {
            synchronized (cacheLock) {
                CachedManager cached = authenticationManagers.get(canonicalIssuerUri);
                if (cached != null) {
                    if (now.isBefore(cached.expiresAt())) {
                        return cached.manager();
                    }
                    authenticationManagers.remove(canonicalIssuerUri);
                }
            }
        }

        CompletableFuture<AuthenticationManager> newFlight = new CompletableFuture<>();
        CompletableFuture<AuthenticationManager> existingFlight = inFlightBuilds.putIfAbsent(canonicalIssuerUri, newFlight);
        if (existingFlight != null) {
            return awaitFlight(existingFlight);
        }

        long capturedGlobalEpoch = globalEvictEpoch.get();
        long capturedIssuerEpoch = issuerEvictEpochs
                .computeIfAbsent(canonicalIssuerUri, k -> new AtomicLong())
                .get();
        try {
            AuthenticationManager built = createAuthenticationManager(canonicalIssuerUri);
            Instant builtAt = clock.instant();
            if (!cacheTtl.isZero()) {
                synchronized (cacheLock) {
                    boolean evictedInFlight = globalEvictEpoch.get() != capturedGlobalEpoch
                            || issuerEvictEpochs.get(canonicalIssuerUri).get() != capturedIssuerEpoch;
                    if (!evictedInFlight) {
                        authenticationManagers.put(
                                canonicalIssuerUri, new CachedManager(built, builtAt.plus(cacheTtl)));
                    }
                }
            }
            newFlight.complete(built);
            return built;
        } catch (Throwable ex) {
            newFlight.completeExceptionally(ex);
            if (ex instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new AuthenticationServiceException("Unable to initialize JWT decoder for issuer.", ex);
        } finally {
            inFlightBuilds.remove(canonicalIssuerUri, newFlight);
        }
    }

    private static AuthenticationManager awaitFlight(CompletableFuture<AuthenticationManager> flight) {
        try {
            return flight.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AuthenticationServiceException("Interrupted while waiting for JWT decoder initialization.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AuthenticationException authEx) {
                throw authEx;
            }
            if (cause instanceof RuntimeException runtimeEx) {
                throw runtimeEx;
            }
            throw new AuthenticationServiceException("Unable to initialize JWT decoder for issuer.", cause);
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (headers == null || !headers.hasMoreElements()) {
            throw new InvalidBearerTokenException("Missing Bearer token.");
        }
        String authorization = headers.nextElement();
        if (headers.hasMoreElements()) {
            throw new InvalidBearerTokenException("Multiple Authorization headers are not permitted.");
        }
        if (authorization == null
                || authorization.length() <= BEARER_PREFIX.length()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new InvalidBearerTokenException("Authorization header must use Bearer scheme.");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty() || token.length() > MAX_JWT_LENGTH) {
            throw new InvalidBearerTokenException("Bearer token is empty or exceeds maximum length.");
        }
        return token;
    }

    private String extractUnverifiedIssuer(String token) {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
            throw new InvalidBearerTokenException("Malformed JWT structure.");
        }
        byte[] payloadBytes;
        try {
            payloadBytes = Base64.getUrlDecoder().decode(segments[1]);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Malformed JWT payload encoding.", ex);
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadBytes);
        } catch (IOException ex) {
            throw new InvalidBearerTokenException("Malformed JWT payload JSON.", ex);
        }
        if (payload == null || !payload.isObject()) {
            throw new InvalidBearerTokenException("JWT payload must be a JSON object.");
        }
        JsonNode issNode = payload.get("iss");
        if (issNode == null || !issNode.isTextual() || issNode.asText().isBlank()) {
            throw new InvalidBearerTokenException("JWT iss claim is missing.");
        }
        return issNode.asText();
    }

    private void validateRouteAndTenantStatus(HttpServletRequest request, String realm) {
        String platformRealm = properties.getPlatformRealm();
        if (isPlatformTenantManagementRoute(request)) {
            if (!realm.equals(platformRealm)) {
                throw new TenantAccessAuthenticationException(
                        ApiErrorCode.TENANT_NOT_ACTIVE,
                        "Tenant management routes require a token from the Platform Control Realm."
                );
            }
            return;
        }

        if (realm.equals(platformRealm) || !TenantIdValidator.isValid(realm)) {
            throw new TenantAccessAuthenticationException(
                    ApiErrorCode.TENANT_NOT_ACTIVE,
                    "Tenant identifier in token issuer is not valid for tenant routes."
            );
        }

        TenantStatus status = tenantLookupService.findStatus(realm)
                .orElseThrow(() -> new TenantAccessAuthenticationException(
                        ApiErrorCode.TENANT_NOT_ACTIVE,
                        "Tenant is not active or does not exist."
                ));
        if (status == TenantStatus.SUSPENDED) {
            throw new TenantAccessAuthenticationException(
                    ApiErrorCode.TENANT_SUSPENDED,
                    "Tenant is suspended."
            );
        }
        if (status != TenantStatus.ACTIVE) {
            throw new TenantAccessAuthenticationException(
                    ApiErrorCode.TENANT_NOT_ACTIVE,
                    "Tenant is not active."
            );
        }
    }

    private AuthenticationManager createAuthenticationManager(String issuerUri) {
        JwtDecoder decoder;
        try {
            decoder = jwtDecoderFactory.create(issuerUri, properties.getExpectedAudience());
        } catch (AuthenticationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AuthenticationServiceException("Unable to initialize JWT decoder for issuer.", ex);
        }
        if (decoder == null) {
            throw new AuthenticationServiceException("JwtDecoderFactory returned null decoder.");
        }
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwtRoleConverter);
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }

    private static boolean isPlatformTenantManagementRoute(HttpServletRequest request) {
        String path = resolveApplicationPath(request);
        return PLATFORM_TENANTS_PATH.equals(path) || path.startsWith(PLATFORM_TENANTS_SUBPATH_PREFIX);
    }

    private static String resolveApplicationPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String requestUri = request.getRequestURI();
        if ((servletPath == null || servletPath.isEmpty())
                && (requestUri == null || requestUri.isEmpty())) {
            return "";
        }
        String resolved = URL_PATH_HELPER.getPathWithinApplication(request);
        if (resolved == null || resolved.isEmpty()) {
            return "";
        }
        return resolved.replaceAll("/+", "/");
    }

    private record CachedManager(AuthenticationManager manager, Instant expiresAt) {
    }
}
