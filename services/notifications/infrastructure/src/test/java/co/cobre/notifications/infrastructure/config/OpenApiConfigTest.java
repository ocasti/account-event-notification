package co.cobre.notifications.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void shouldAddUnauthorizedResponseWhenOperationIsCustomized() {
        var operation = new Operation().responses(new ApiResponses());

        var customized = config.unauthorizedResponseCustomizer().customize(operation, null);

        var unauthorized = customized.getResponses().get("401");
        assertThat(unauthorized.getDescription()).isEqualTo("Missing or invalid bearer token");
        assertThat(unauthorized.getContent().get("application/json").getSchema().get$ref())
            .isEqualTo("#/components/schemas/ErrorResponse");
    }

    @Test
    void shouldRegisterErrorResponseSchemaWhenSchemaIsMissing() {
        var openApi = new OpenAPI().components(new Components());

        config.errorResponseSchemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).containsKey("ErrorResponse");
    }

    @Test
    void shouldNotOverwriteErrorResponseSchemaWhenSchemaAlreadyRegistered() {
        var existingSchema = new Schema<>();
        var openApi = new OpenAPI().components(new Components().addSchemas("ErrorResponse", existingSchema));

        config.errorResponseSchemaCustomizer().customise(openApi);

        assertThat(openApi.getComponents().getSchemas().get("ErrorResponse")).isSameAs(existingSchema);
    }
}
