package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import by.darkimpulsepoint.userservice.repository.UserRepository;
import by.darkimpulsepoint.userservice.security.JwtClaimNames;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static by.darkimpulsepoint.userservice.config.CacheConfig.USER_WITH_CARDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final int REDIS_PORT = 6379;
    protected static final String JWT_SECRET = "test-secret-key-for-integration-tests-hs256";
    protected static final String JWT_ISSUER = "auth-service";

    private static final JwtEncoder JWT_ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(
            new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8")).withExposedPorts(REDIS_PORT);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("security.jwt.secret", () -> JWT_SECRET);
        registry.add("security.jwt.issuer", () -> JWT_ISSUER);
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PaymentCardRepository cardRepository;
    @Autowired
    protected CacheManager cacheManager;
    /** Client authenticated as ADMIN, used by tests that check business logic rather than access rules. */
    protected RestClient rest;
    protected RestClient anonymous;

    @BeforeEach
    void setUpClientAndCleanState() {
        anonymous = clientBuilder().build();
        rest = clientWithToken(adminToken());

        cardRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        Objects.requireNonNull(cacheManager.getCache(USER_WITH_CARDS)).clear();
    }

    protected RestClient clientWithToken(String token) {
        return clientBuilder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    protected static String adminToken() {
        return token(null, "ADMIN", "access", Duration.ofMinutes(15));
    }

    protected static String userToken(Long userId) {
        return token(userId, "USER", "access", Duration.ofMinutes(15));
    }

    protected static String token(Long userId, String role, String type, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JWT_ISSUER)
                .subject(userId == null ? "admin" : "user-" + userId)
                .issuedAt(ttl.isNegative() ? now.plus(ttl).minusSeconds(60) : now.minusSeconds(1))
                .expiresAt(now.plus(ttl))
                .claim(JwtClaimNames.ROLE, role)
                .claim(JwtClaimNames.TOKEN_TYPE, type);
        if (userId != null) {
            claims.claim(JwtClaimNames.USER_ID, userId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return JWT_ENCODER.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private RestClient.Builder clientBuilder() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                });
    }

    protected Cache userCache() {
        return Objects.requireNonNull(cacheManager.getCache(USER_WITH_CARDS));
    }
}
