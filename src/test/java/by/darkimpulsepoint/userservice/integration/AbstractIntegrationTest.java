package by.darkimpulsepoint.userservice.integration;

import by.darkimpulsepoint.userservice.repository.PaymentCardRepository;
import by.darkimpulsepoint.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

import static by.darkimpulsepoint.userservice.config.CacheConfig.USER_WITH_CARDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final int REDIS_PORT = 6379;

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
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PaymentCardRepository cardRepository;
    @Autowired
    protected CacheManager cacheManager;
    protected RestClient rest;

    @BeforeEach
    void setUpClientAndCleanState() {
        rest = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();

        cardRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        Objects.requireNonNull(cacheManager.getCache(USER_WITH_CARDS)).clear();
    }

    protected Cache userCache() {
        return Objects.requireNonNull(cacheManager.getCache(USER_WITH_CARDS));
    }
}
