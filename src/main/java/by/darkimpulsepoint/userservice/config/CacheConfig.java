package by.darkimpulsepoint.userservice.config;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_WITH_CARDS = "userWithCards";

    @Bean
    public RedisCacheManagerBuilderCustomizer transactionAwareCaches() {
        return builder -> builder.transactionAware();
    }
}
