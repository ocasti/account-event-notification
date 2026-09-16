package co.cobre.notifications.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuration for system clock.
 */
@Configuration
public class ClockConfig {

    
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
