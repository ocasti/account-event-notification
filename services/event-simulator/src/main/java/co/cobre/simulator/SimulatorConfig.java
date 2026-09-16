package co.cobre.simulator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.random.RandomGenerator;

@Configuration
public class SimulatorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator randomGenerator() {
        // Use java.util.Random instead of RandomGenerator.getDefault() for compatibility with slim JRE images where jdk.random module is unavailable.
        return new java.util.Random();
    }
}
