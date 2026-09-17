package co.cobre.notifications.infrastructure.config;

import co.cobre.notifications.infrastructure.rest.ErrorResponse;
import co.cobre.notifications.infrastructure.security.AuthenticatedClient;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI documentation configuration. Only takes effect where springdoc itself is enabled
 * (the "local" profile, per application-local.yaml); no {@code @Profile} is needed here.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";
    private static final String ERROR_SCHEMA_NAME = "ErrorResponse";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA_NAME;

    static {
        // Hides the @AuthenticatedClient-annotated ClientId argument (resolved server-side
        // from the JWT by AuthenticatedClientArgumentResolver) from the generated spec, so it
        // never shows up as a spurious "clientId" query parameter.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(AuthenticatedClient.class);
    }

    /**
     * Builds the OpenAPI document metadata: title, version, description, and the global
     * Bearer JWT security scheme applied to every operation.
     */
    @Bean
    public OpenAPI notificationsOpenApi(
        ObjectProvider<BuildProperties> buildProperties,
        @Value("${project.version:0.1.0-SNAPSHOT}") String fallbackVersion
    ) {
        String version = resolveVersion(buildProperties, fallbackVersion);

        return new OpenAPI()
            .info(new Info()
                .title("Account Event Notification API")
                .version(version)
                .description(
                    "Self-service API for a client to query its notification events and replay "
                        + "the failed ones. Authentication is Bearer JWT (RS256), and returned data "
                        + "is always scoped to the token's owning client."
                ))
            .components(new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    private String resolveVersion(ObjectProvider<BuildProperties> buildProperties, String fallbackVersion) {
        return buildProperties.stream().map(BuildProperties::getVersion).findFirst().orElse(fallbackVersion);
    }

    /**
     * Adds the 401 response every operation can return to Spring Security rejecting a request
     * before it reaches a controller (no {@code AuthenticationException} is thrown from
     * application code, so {@link co.cobre.notifications.infrastructure.rest.ApiExceptionHandler}
     * never sees it and can't document it itself).
     */
    @Bean
    public OperationCustomizer unauthorizedResponseCustomizer() {
        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("401", unauthorizedResponse());
            return operation;
        };
    }

    private ApiResponse unauthorizedResponse() {
        return new ApiResponse()
            .description("Missing or invalid bearer token")
            .content(new Content().addMediaType("application/json", errorResponseMediaType()));
    }

    private MediaType errorResponseMediaType() {
        return new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF));
    }

    /**
     * Registers the {@link ErrorResponse} schema in {@code components.schemas} even though no
     * controller method returns it directly from its signature (every error body is produced by
     * {@code ApiExceptionHandler}), so the {@code $ref} added by
     * {@link #unauthorizedResponseCustomizer()} resolves.
     */
    @Bean
    public OpenApiCustomizer errorResponseSchemaCustomizer() {
        return openApi -> {
            var schemas = openApi.getComponents().getSchemas();
            boolean alreadyRegistered = schemas != null && schemas.containsKey(ERROR_SCHEMA_NAME);
            if (!alreadyRegistered) {
                ResolvedSchema resolved = ModelConverters.getInstance().readAllAsResolvedSchema(ErrorResponse.class);
                resolved.referencedSchemas.forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            }
        };
    }
}
