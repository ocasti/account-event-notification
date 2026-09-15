package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.domain.policy.RetryPolicy;
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
     * Creates a random generator bean.
     */
    @Bean
    RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }
}
