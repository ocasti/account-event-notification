package co.cobre.notifications;

import co.cobre.notifications.application.UseCase;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "co.cobre.notifications",
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = UseCase.class
    ),
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
        @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class)
    }
)
@ConfigurationPropertiesScan
public class NotificationsApplication {

    /**
     * Starts the notification service.
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationsApplication.class, args);
    }
}
