package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.domain.exception.IllegalStateTransitionException;
import co.cobre.notifications.domain.exception.NotificationEventNotFoundException;
import co.cobre.notifications.domain.exception.ReplayNotAllowedException;
import co.cobre.notifications.domain.model.*;
import co.cobre.notifications.infrastructure.rest.mapper.NotificationEventResponseMapper;
import co.cobre.notifications.infrastructure.security.ClientIdResolver;
import co.cobre.notifications.infrastructure.security.JwtProperties;
import co.cobre.notifications.infrastructure.security.RestTestSecurityConfig;
import co.cobre.notifications.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static co.cobre.notifications.infrastructure.security.RestTestSecurityConfig.token;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationEventController.class)
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, ClientIdResolver.class, NotificationEventResponseMapper.class, ApiExceptionHandler.class})
class NotificationEventControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotificationEvents listNotificationEvents;

    @MockitoBean
    private GetNotificationEvent getNotificationEvent;

    @MockitoBean
    private ReplayNotificationEvent replayNotificationEvent;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("notifications.jwt.public-key", () -> "file:" + RestTestSecurityConfig.publicKeyFile());
        r.add("notifications.jwt.audience", () -> "account-event-notification");
        r.add("notifications.jwt.client-claim", () -> "sub");
    }

    @Test
    void list_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/notification_events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer invalid.token.here"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withValidToken_returnsEvents() throws Exception {
        ClientId clientId = new ClientId("CLIENT002");
        NotificationEvent event = new NotificationEvent(
            new EventId("EVT001"),
            clientId,
            new EventKey("user.created"),
            "test content",
            Instant.parse("2024-03-15T10:00:00Z"),
            Instant.parse("2024-03-15T10:00:00Z"),
            DeliveryStatus.FAILED,
            Optional.of("sub123"),
            1,
            Optional.empty()
        );

        when(listNotificationEvents.list(any()))
            .thenReturn(new co.cobre.notifications.application.query.NotificationEventPage(
                List.of(event),
                Optional.of("cursor123")
            ));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer " + jwtToken)
            .param("delivery_status", "failed")
            .param("from", "2024-03-15T00:00:00Z")
            .param("to", "2024-03-16T00:00:00Z")
            .param("limit", "20")
            .param("cursor", "abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].event_id").value("EVT001"))
            .andExpect(jsonPath("$.items[0].event_type").value("user.created"))
            .andExpect(jsonPath("$.items[0].client_id").value("CLIENT002"))
            .andExpect(jsonPath("$.items[0].content").value("test content"))
            .andExpect(jsonPath("$.items[0].delivery_status").value("failed"))
            .andExpect(jsonPath("$.next_cursor").value("cursor123"));

        verify(listNotificationEvents).list(argThat(query ->
            query.clientId().value().equals("CLIENT002") &&
            query.status().isPresent() && query.status().get() == DeliveryStatus.FAILED &&
            query.limit() == 20 &&
            query.cursor().isPresent() && query.cursor().get().equals("abc")
        ));
    }

    @Test
    void list_withInvalidDeliveryStatus_returns400() throws Exception {
        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer " + jwtToken)
            .param("delivery_status", "bogus"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_parameter"));
    }

    @Test
    void list_withLimitZero_returns400() throws Exception {
        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer " + jwtToken)
            .param("limit", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void list_withLimitTooHigh_returns400() throws Exception {
        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer " + jwtToken)
            .param("limit", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void list_withoutLimit_defaultsTo20() throws Exception {
        ClientId clientId = new ClientId("CLIENT002");
        when(listNotificationEvents.list(any()))
            .thenReturn(new co.cobre.notifications.application.query.NotificationEventPage(
                List.of(),
                Optional.empty()
            ));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk());

        verify(listNotificationEvents).list(argThat(query -> query.limit() == 20));
    }

    @Test
    void get_withValidToken_returnsDetail() throws Exception {
        ClientId clientId = new ClientId("CLIENT002");
        EventId eventId = new EventId("EVT003");
        NotificationEvent event = new NotificationEvent(
            eventId,
            clientId,
            new EventKey("user.created"),
            "test content",
            Instant.parse("2024-03-15T10:00:00Z"),
            Instant.parse("2024-03-15T10:00:00Z"),
            DeliveryStatus.PENDING,
            Optional.of("sub123"),
            1,
            Optional.empty()
        );

        DeliveryAttempt attempt = new DeliveryAttempt(
            java.util.UUID.randomUUID(),
            eventId,
            1,
            1,
            Instant.parse("2024-03-15T10:30:00Z"),
            Optional.empty(),
            Optional.empty(),
            Optional.of(Instant.parse("2024-03-15T10:15:00Z")),
            Optional.of(500),
            Optional.empty(),
            Optional.of(java.time.Duration.ofMillis(150)),
            AttemptOrigin.SYSTEM
        );

        when(getNotificationEvent.get(any(), any()))
            .thenReturn(new co.cobre.notifications.application.query.NotificationEventDetail(
                event,
                List.of(attempt)
            ));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events/EVT003")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.event_id").value("EVT003"))
            .andExpect(jsonPath("$.attempts").isArray())
            .andExpect(jsonPath("$.attempts[0].cycle").value(1))
            .andExpect(jsonPath("$.attempts[0].attempt_number").value(1))
            .andExpect(jsonPath("$.attempts[0].response_status").value(500))
            .andExpect(jsonPath("$.attempts[0].latency_ms").value(150))
            .andExpect(jsonPath("$.attempts[0].origin").value("system"));
    }

    @Test
    void get_withNonExistentId_returns404() throws Exception {
        when(getNotificationEvent.get(any(), any()))
            .thenThrow(new NotificationEventNotFoundException(new EventId("EVT999")));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events/EVT999")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void replay_withValidToken_returns202() throws Exception {
        ClientId clientId = new ClientId("CLIENT002");
        EventId eventId = new EventId("EVT003");

        when(replayNotificationEvent.replay(any(), any()))
            .thenReturn(new co.cobre.notifications.application.query.ReplayResult(
                eventId,
                2
            ));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.event_id").value("EVT003"))
            .andExpect(jsonPath("$.cycle").value(2))
            .andExpect(jsonPath("$.delivery_status").value("pending"));
    }

    @Test
    void replay_notAllowed_returns409() throws Exception {
        when(replayNotificationEvent.replay(any(), any()))
            .thenThrow(new ReplayNotAllowedException(new EventId("EVT003"), DeliveryStatus.COMPLETED));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("replay_not_allowed"));
    }

    @Test
    void replay_illegalTransition_returns409() throws Exception {
        when(replayNotificationEvent.replay(any(), any()))
            .thenThrow(new IllegalStateTransitionException(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING));

        String jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("illegal_transition"));
    }
}
