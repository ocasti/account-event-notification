package co.cobre.simulator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.random.RandomGenerator;

/**
 * Configuration beans for the simulator.
 */
@Configuration
public class SimulatorConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }
}
