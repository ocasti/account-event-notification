package co.cobre.notifications.domain;

import java.time.Instant;
import java.util.stream.Stream;

import co.cobre.notifications.domain.fixtures.Clocks;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class EventDataTest {
    private static final EventId EVENT_ID = new EventId("test-event-id");
    private static final ClientId CLIENT_ID = new ClientId("test-client");
    private static final EventKey EVENT_KEY = new EventKey("test.event.key");
    private static final String CONTENT = "test content";
    private static final Instant OCCURRED_AT = Clocks.NOW;

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredFields")
    void shouldRejectEventDataWhenRequiredFieldIsNull(String caseName, ThrowingCallable construction) {
        assertThatThrownBy(construction).isInstanceOf(NullPointerException.class);
    }

    private static Stream<Arguments> missingRequiredFields() {
        return Stream.of(
            Arguments.of(
                "null event id",
                (ThrowingCallable) () -> new EventData(null, CLIENT_ID, EVENT_KEY, CONTENT, OCCURRED_AT)),
            Arguments.of(
                "null client id",
                (ThrowingCallable) () -> new EventData(EVENT_ID, null, EVENT_KEY, CONTENT, OCCURRED_AT)),
            Arguments.of(
                "null event key",
                (ThrowingCallable) () -> new EventData(EVENT_ID, CLIENT_ID, null, CONTENT, OCCURRED_AT)),
            Arguments.of(
                "null content",
                (ThrowingCallable) () -> new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, null, OCCURRED_AT)),
            Arguments.of(
                "null occurred at",
                (ThrowingCallable) () -> new EventData(EVENT_ID, CLIENT_ID, EVENT_KEY, CONTENT, null)));
    }
}
