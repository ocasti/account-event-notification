package co.cobre.notifications.boot;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ProcessDueDeliveries;
import co.cobre.notifications.application.usecase.RegisterNotificationEvent;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.infrastructure.worker.AccountEventListener;
import co.cobre.notifications.infrastructure.worker.DeliveryScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boot test for API profile: validates that the application starts correctly
 * with api and local profiles, exposes required endpoints, and has the correct beans.
 * Uses singleton Testcontainers (PostgreSQL and ElasticMQ) configured in parent class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"api", "local"})
class ApiProfileBootIT extends BootTestSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        BootTestSupport.registerBootProperties(registry);
    }

    static Stream<Arguments> useCaseBeans() {
        return Stream.of(
            Arguments.of(RegisterNotificationEvent.class),
            Arguments.of(ListNotificationEvents.class),
            Arguments.of(GetNotificationEvent.class),
            Arguments.of(ReplayNotificationEvent.class),
            Arguments.of(ProcessDueDeliveries.class)
        );
    }

    static Stream<Arguments> workerOnlyBeans() {
        return Stream.of(
            Arguments.of(AccountEventListener.class),
            Arguments.of(DeliveryScheduler.class)
        );
    }

    @Test
    void shouldLoadContextWhenApiProfileIsActive() {
        assertThat(context).isNotNull();
    }

    @Test
    void shouldRespondOkWhenReadinessProbeIsCalledWithoutToken() throws Exception {
        var request = get("/actuator/health/readiness");

        var result = mockMvc.perform(request);

        result.andExpect(status().isOk());
    }

    @Test
    void shouldRejectWithUnauthorizedWhenNotificationEventsAreListedWithoutToken() throws Exception {
        var request = get("/notification_events");

        var result = mockMvc.perform(request);

        result.andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} is not registered under the api profile")
    @MethodSource("workerOnlyBeans")
    void shouldNotRegisterWorkerBeanWhenApiProfileIsActive(Class<?> workerBean) {
        var beans = context.getBeansOfType(workerBean);

        assertThat(beans).isEmpty();
    }

    @ParameterizedTest(name = "{0} is registered under the api profile")
    @MethodSource("useCaseBeans")
    void shouldRegisterUseCaseBeanWhenApiProfileIsActive(Class<?> useCase) {
        var beans = context.getBeansOfType(useCase);

        assertThat(beans).isNotEmpty();
    }

    @Test
    void shouldSeedThreeSubscriptionsWhenFlywayMigrationsAreApplied() {
        var query = "SELECT count(*) FROM subscriptions";

        Integer subscriptionCount = jdbcTemplate.queryForObject(query, Integer.class);

        assertThat(subscriptionCount).isEqualTo(3);
    }
}
