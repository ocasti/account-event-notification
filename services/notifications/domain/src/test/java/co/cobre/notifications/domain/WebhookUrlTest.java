package co.cobre.notifications.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookUrlTest {

    @ParameterizedTest(name = "shouldAcceptWebhookUrlWhenSchemeIs{1}")
    @CsvSource({
        "https://example.com/webhook,https",
        "HTTPS://example.com/webhook,https",
        "http://example.com/webhook,http"
    })
    void shouldAcceptWebhookUrlWhenSchemeIsHttpOrHttps(String rawUri, String expectedScheme) {
        var url = new WebhookUrl(URI.create(rawUri));

        assertThat(url.value().getScheme()).isEqualToIgnoringCase(expectedScheme);
        assertThat(url.value().getHost()).isEqualTo("example.com");
    }

    @Test
    void shouldAcceptWebhookUrlWhenUrlHasQueryString() {
        var url = new WebhookUrl(URI.create("https://example.com/webhook?token=abc"));

        assertThat(url.value().getQuery()).isEqualTo("token=abc");
    }

    @Test
    void shouldAcceptWebhookUrlWhenUrlHasExplicitPort() {
        var url = new WebhookUrl(URI.create("https://example.com:8443/webhook"));

        assertThat(url.value().getPort()).isEqualTo(8443);
    }

    @Test
    void shouldCreateWebhookUrlWhenUsingValidStringFactory() {
        var url = WebhookUrl.of("https://example.com/webhook");

        assertThat(url.value().getHost()).isEqualTo("example.com");
    }

    @Test
    void shouldAcceptWebhookUrlWhenUsingHttpStringFactory() {
        var url = WebhookUrl.of("http://example.com/webhook");

        assertThat(url.value().getScheme()).isEqualToIgnoringCase("http");
    }

    @Test
    void shouldRejectWebhookUrlWhenValueIsNull() {
        assertThatThrownBy(() -> new WebhookUrl(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest(name = "shouldRejectWebhookUrlWhenRawValueIs{0}")
    @ValueSource(strings = {
        "example.com/webhook",
        "https:///webhook",
        "https://example.com/webhook#section",
        "ftp://example.com/webhook"
    })
    void shouldRejectWebhookUrlWhenUrlFormatIsInvalid(String rawUri) {
        var uri = URI.create(rawUri);

        assertThatThrownBy(() -> new WebhookUrl(uri))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("HTTP or HTTPS");
    }

    @Test
    void shouldRejectWebhookUrlWhenStringFactoryReceivesInvalidUri() {
        assertThatThrownBy(() -> WebhookUrl.of("not a valid uri"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid webhook URL");
    }
}
