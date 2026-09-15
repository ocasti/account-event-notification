package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.domain.RetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.random.RandomGenerator;

/**
 * Configuration for retry behavior.
 */
@Configuration
public class RetryConfig {

    /**
     * Creates a retry policy bean.
     */
    @Bean
    RetryPolicy retryPolicy() {
        return RetryPolicy.standard();
    }

    /**
     * Creates a random generator bean using java.util.Random to ensure compatibility with slim JRE images where jdk.random module is unavailable.
     */
    @Bean
    RandomGenerator randomGenerator() {
        return new java.util.Random();
    }
}
