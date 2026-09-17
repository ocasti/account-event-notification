package co.cobre.notifications.infrastructure.rest;

import co.cobre.notifications.application.usecase.GetNotificationEvent;
import co.cobre.notifications.application.usecase.ListNotificationEvents;
import co.cobre.notifications.application.usecase.NotificationEventDetail;
import co.cobre.notifications.application.usecase.NotificationEventSummary;
import co.cobre.notifications.application.usecase.NotificationEventSummaryPage;
import co.cobre.notifications.application.usecase.ListNotificationEventsQuery;
import co.cobre.notifications.application.usecase.ReplayNotificationEvent;
import co.cobre.notifications.application.usecase.ReplayResult;
import co.cobre.notifications.domain.AttemptOrigin;
import co.cobre.notifications.domain.DeliveryStatus;
import co.cobre.notifications.domain.EventId;
import co.cobre.notifications.domain.EventKey;
import co.cobre.notifications.domain.IllegalStateTransitionException;
import co.cobre.notifications.domain.NotificationEventNotFoundException;
import co.cobre.notifications.domain.ReplayNotAllowedException;
import co.cobre.notifications.domain.fixtures.DeliveryAttempts;
import co.cobre.notifications.domain.fixtures.Ids;
import co.cobre.notifications.domain.fixtures.NotificationEvents;
import co.cobre.notifications.infrastructure.security.AuthenticatedClientArgumentResolver;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static co.cobre.notifications.infrastructure.security.RestTestSecurityConfig.token;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationEventController.class)
@EnableConfigurationProperties(JwtProperties.class)
@Import({SecurityConfig.class, ClientIdResolver.class, AuthenticatedClientArgumentResolver.class, WebMvcConfig.class, NotificationEventResponseMapper.class, ApiExceptionHandler.class})
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
    void shouldReturn401WhenListRequestHasNoToken() throws Exception {
        mockMvc.perform(get("/notification_events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenListRequestHasInvalidToken() throws Exception {
        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer invalid.token.here"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnEventsWhenListRequestHasValidToken() throws Exception {
        var query = new ListNotificationEventsQuery(
            Ids.CLIENT_002,
            Optional.of(Instant.parse("2024-03-15T00:00:00Z")),
            Optional.of(Instant.parse("2024-03-16T00:00:00Z")),
            Optional.of(DeliveryStatus.FAILED),
            20,
            Optional.of("abc")
        );
        var event = NotificationEvents.aPendingEvent()
            .withEventId(Ids.EVT_001)
            .withClientId(Ids.CLIENT_002)
            .withEventKey(new EventKey("user.created"))
            .withStatus(DeliveryStatus.FAILED)
            .withCycle(1)
            .build();
        when(listNotificationEvents.list(query))
            .thenReturn(new NotificationEventSummaryPage(
                List.of(new NotificationEventSummary(event, 5)),
                Optional.of("cursor123")
            ));
        var jwtToken = token("CLIENT002");

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
            .andExpect(jsonPath("$.items[0].content").value(event.content()))
            .andExpect(jsonPath("$.items[0].delivery_status").value("failed"))
            .andExpect(jsonPath("$.items[0].attempts_count").value(5))
            .andExpect(jsonPath("$.next_cursor").value("cursor123"));

        verify(listNotificationEvents).list(query);
    }

    @Test
    void shouldReturn400WhenListRequestHasInvalidDeliveryStatus() throws Exception {
        var jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer " + jwtToken)
                .param("delivery_status", "bogus"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("invalid_parameter"));
    }

    @Test
    void shouldReturn400WhenListRequestHasLimitZero() throws Exception {
        var jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer " + jwtToken)
                .param("limit", "0"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void shouldReturn400WhenListRequestHasLimitTooHigh() throws Exception {
        var jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer " + jwtToken)
                .param("limit", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void shouldDefaultLimitTo20WhenListRequestOmitsLimit() throws Exception {
        var query = new ListNotificationEventsQuery(
            Ids.CLIENT_002, Optional.empty(), Optional.empty(), Optional.empty(), 20, Optional.empty()
        );
        when(listNotificationEvents.list(query))
            .thenReturn(new NotificationEventSummaryPage(
                List.of(),
                Optional.empty()
            ));
        var jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isOk());

        verify(listNotificationEvents).list(query);
    }

    @Test
    void shouldReturnDetailWhenGetRequestHasValidToken() throws Exception {
        var event = NotificationEvents.aPendingEvent()
            .withEventId(Ids.EVT_003)
            .withClientId(Ids.CLIENT_002)
            .withEventKey(new EventKey("user.created"))
            .withCycle(1)
            .build();
        var attempt = DeliveryAttempts.anAttempt()
            .withEventId(Ids.EVT_003)
            .withCycle(1)
            .withAttemptNumber(1)
            .withNextAttemptAt(Instant.parse("2024-03-15T10:30:00Z"))
            .withExecutedAt(Instant.parse("2024-03-15T10:15:00Z"))
            .withResponseStatus(500)
            .withLatency(Duration.ofMillis(150))
            .withOrigin(AttemptOrigin.SYSTEM)
            .build();
        when(getNotificationEvent.get(Ids.CLIENT_002, Ids.EVT_003))
            .thenReturn(new NotificationEventDetail(event, List.of(attempt)));
        var jwtToken = token("CLIENT002");

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
    void shouldReturn404WhenGetRequestHasNonExistentId() throws Exception {
        when(getNotificationEvent.get(Ids.CLIENT_002, new EventId("EVT999")))
            .thenThrow(new NotificationEventNotFoundException(new EventId("EVT999")));
        var jwtToken = token("CLIENT002");

        mockMvc.perform(get("/notification_events/EVT999")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void shouldReturn202WhenReplayRequestHasValidToken() throws Exception {
        when(replayNotificationEvent.replay(Ids.CLIENT_002, Ids.EVT_003))
            .thenReturn(new ReplayResult(Ids.EVT_003, 2));
        var jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.event_id").value("EVT003"))
            .andExpect(jsonPath("$.cycle").value(2))
            .andExpect(jsonPath("$.delivery_status").value("pending"));
    }

    @Test
    void shouldReturn409WhenReplayEndpointRejectsNonFailedEvent() throws Exception {
        when(replayNotificationEvent.replay(Ids.CLIENT_002, Ids.EVT_003))
            .thenThrow(new ReplayNotAllowedException(Ids.EVT_003, DeliveryStatus.COMPLETED));
        var jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("replay_not_allowed"));
    }

    @Test
    void shouldReturn409WhenReplayHasIllegalTransition() throws Exception {
        when(replayNotificationEvent.replay(Ids.CLIENT_002, Ids.EVT_003))
            .thenThrow(new IllegalStateTransitionException(DeliveryStatus.COMPLETED, DeliveryStatus.PENDING));
        var jwtToken = token("CLIENT002");

        mockMvc.perform(post("/notification_events/EVT003/replay")
                .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("illegal_transition"));
    }
}
