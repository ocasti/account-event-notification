package co.cobre.notifications.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlTest {

    @Test
    void shouldAcceptValidHttpsUrl() {
        var url = new WebhookUrl(URI.create("https://example.com/webhook"));

        assertThat(url.value().getScheme()).isEqualTo("https");
        assertThat(url.value().getHost()).isEqualTo("example.com");
    }

    @Test
    void shouldAcceptHttpsWithUpperCase() {
        var url = new WebhookUrl(URI.create("HTTPS://example.com/webhook"));

        assertThat(url.value().getScheme()).isEqualToIgnoringCase("https");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new WebhookUrl(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectHttpUrl() {
        assertThatThrownBy(() -> new WebhookUrl(URI.create("http://example.com/webhook")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be a valid HTTPS URL");
    }

    @Test
    void shouldRejectUrlWithoutScheme() {
        assertThatThrownBy(() -> new WebhookUrl(URI.create("example.com/webhook")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUrlWithoutHost() {
        assertThatThrownBy(() -> new WebhookUrl(URI.create("https:///webhook")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be a valid HTTPS URL");
    }

    @Test
    void shouldRejectUrlWithFragment() {
        assertThatThrownBy(() -> new WebhookUrl(URI.create("https://example.com/webhook#section")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be a valid HTTPS URL");
    }

    @Test
    void shouldAcceptUrlWithQuery() {
        var url = new WebhookUrl(URI.create("https://example.com/webhook?token=abc"));

        assertThat(url.value().getQuery()).isEqualTo("token=abc");
    }

    @Test
    void shouldAcceptUrlWithPort() {
        var url = new WebhookUrl(URI.create("https://example.com:8443/webhook"));

        assertThat(url.value().getPort()).isEqualTo(8443);
    }

    @Test
    void shouldCreateFromValidStringUsingFactory() {
        var url = WebhookUrl.of("https://example.com/webhook");

        assertThat(url.value().getHost()).isEqualTo("example.com");
    }

    @Test
    void shouldThrowWhenCreatingFromInvalidStringUsingFactory() {
        assertThatThrownBy(() -> WebhookUrl.of("not a valid uri"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid webhook URL");
    }

    @Test
    void shouldThrowWhenCreatingFromHttpUrlUsingFactory() {
        assertThatThrownBy(() -> WebhookUrl.of("http://example.com/webhook"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
