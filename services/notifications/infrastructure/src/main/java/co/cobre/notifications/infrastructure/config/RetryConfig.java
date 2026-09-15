package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.domain.RetryPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.random.RandomGenerator;

@Configuration
@EnableConfigurationProperties(RetryProperties.class)
public class RetryConfig {

    @Bean
    RetryPolicy retryPolicy(RetryProperties props) {
        return new RetryPolicy(
            props.baseDelay(),
            props.factor(),
            props.maxDelay(),
            props.jitterRatio(),
            props.maxAttempts()
        );
    }

    @Bean
    RandomGenerator randomGenerator() {
        return new java.util.Random();
    }
}
