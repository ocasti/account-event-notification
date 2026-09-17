package co.cobre.notifications.boot;

import co.cobre.notifications.application.port.NotificationEventRepository;
import co.cobre.notifications.application.port.WebhookSender;
import co.cobre.notifications.infrastructure.worker.AccountEventListener;
import co.cobre.notifications.infrastructure.worker.DeliveryScheduler;
import co.cobre.notifications.infrastructure.worker.MeteredNotificationEventRepository;
import co.cobre.notifications.infrastructure.worker.MeteredWebhookSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot test for worker profile: validates that the application starts correctly
 * with worker and local profiles, has worker-specific beans, and exposes metrics endpoints.
 *
 * Note: spring.flyway.enabled=true is set in this test even though the worker profile
 * does not migrate in production. This is necessary for tests to populate the database schema.
 *
 * Uses singleton Testcontainers (PostgreSQL and ElasticMQ) configured in parent class.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.flyway.enabled=true"
)
@AutoConfigureMockMvc
@ActiveProfiles({"worker", "local"})
class WorkerProfileBootIT extends BootTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        BootTestSupport.registerBootProperties(registry);
    }

    static Stream<Arguments> workerBeans() {
        return Stream.of(
            Arguments.of(AccountEventListener.class),
            Arguments.of(DeliveryScheduler.class)
        );
    }

    static Stream<Arguments> meteredPorts() {
        return Stream.of(
            Arguments.of(WebhookSender.class, MeteredWebhookSender.class),
            Arguments.of(NotificationEventRepository.class, MeteredNotificationEventRepository.class)
        );
    }

    @Test
    void shouldLoadContextWhenWorkerProfileIsActive() {
        assertThat(context).isNotNull();
    }

    @ParameterizedTest(name = "{0} is registered under the worker profile")
    @MethodSource("workerBeans")
    void shouldRegisterWorkerBeanWhenWorkerProfileIsActive(Class<?> workerBean) {
        var beans = context.getBeansOfType(workerBean);

        assertThat(beans).isNotEmpty();
    }

    @ParameterizedTest(name = "{0} resolves to {1}")
    @MethodSource("meteredPorts")
    void shouldResolvePortToMeteredDecoratorWhenWorkerProfileIsActive(Class<?> port, Class<?> decorator) {
        var bean = context.getBean(port);

        assertThat(bean).isInstanceOf(decorator);
    }

    @Test
    void shouldRespondOkWhenReadinessProbeIsCalledOnWorker() throws Exception {
        var request = get("/actuator/health/readiness");

        var result = mockMvc.perform(request);

        result.andExpect(status().isOk());
    }

    @Test
    void shouldExposeNotificationsMetricsWhenPrometheusEndpointIsScraped() throws Exception {
        var request = get("/actuator/prometheus");

        var result = mockMvc.perform(request);

        result.andExpect(status().isOk())
            .andExpect(content().string(containsString("notifications_")));
    }
}
