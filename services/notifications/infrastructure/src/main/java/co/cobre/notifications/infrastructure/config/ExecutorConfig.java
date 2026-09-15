package co.cobre.notifications.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {

    @Bean
    @Primary
    Executor deliveryExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
