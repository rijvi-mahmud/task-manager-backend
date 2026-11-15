package com.example.backend.config;

import com.example.backend.common.api.ProblemResponses;
import com.example.backend.common.api.ApiTags;
import com.example.backend.common.api.ApiVersions;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

/**
 * OpenAPI 3.1 document metadata plus the shared error contract.
 *
 * <p>Errors are declared once as reusable components ({@code #/components/responses/*})
 * and referenced from each operation via {@link ProblemResponses}, so every
 * documented failure has the same RFC 9457 shape the
 * {@link com.example.backend.common.api.GlobalExceptionHandler} actually emits.
 */
@Configuration
public class OpenApiConfig {

    static final String PROBLEM_SCHEMA = "Problem";
    public static final String SESSION_SCHEME = "session";

    static {
        // Enums become named, reusable schemas (TaskStatus, TaskPriority) instead of
        // being inlined in every model; generated clients then get real enum types.
        ModelResolver.enumsAsRef = true;
    }

    @Bean
    OpenAPI taskManagerOpenApi() {
        return new OpenAPI()
                // Relative server: the document is valid wherever the API is deployed.
                .servers(List.of(new Server().url("/").description("This server")))
                // Every operation needs a session unless it declares `security: []` (the auth endpoints).
                .addSecurityItem(new SecurityRequirement().addList(SESSION_SCHEME))
                .info(new Info()
                        .title("Task Manager API")
                        .version(ApiVersions.V1)
                        .description("""
                                Shared Kanban boards (workspaces) with tasks assignable to workspace members.

                                * **Authentication:** sign in with `POST /api/v1/auth/login`; the server sets an \
                                HttpOnly `SESSION` cookie that the browser sends automatically.
                                * **CSRF:** every `POST`, `PUT`, `PATCH` and `DELETE` must send the value of the \
                                `XSRF-TOKEN` cookie in the `X-XSRF-TOKEN` header (`GET /api/v1/auth/csrf` sets the cookie). \
                                A missing or wrong token returns `403`.
                                * **Access:** workspaces you are not a member of, and everything in them, return `404`. \
                                Actions that need the owner role return `403`.
                                * **Versioning:** the major version is part of the path (`/api/v1/...`). \
                                Unsupported versions return `400`.
                                * **Errors:** every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) \
                                problem (`application/problem+json`).
                                * **Concurrency:** full task updates carry the `version` the client last read; \
                                a stale version returns `409`.
                                """))
                .addTagsItem(new Tag().name(ApiTags.AUTH).description("Register, sign in and out, reset passwords"))
                .addTagsItem(new Tag().name(ApiTags.WORKSPACES).description("Shared boards and their owners"))
                .addTagsItem(new Tag().name(ApiTags.MEMBERS).description("Who can access a workspace and be assigned its tasks"))
                .addTagsItem(new Tag().name(ApiTags.TASKS).description("Create, update, move and assign tasks in a workspace"))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("SESSION")
                                .description("Session cookie set by `login` or `register`."))
                        .addSchemas(PROBLEM_SCHEMA, problemSchema())
                        .addSchemas("ResponseMetadata", metadataSchema())
                        .addResponses(ProblemResponses.BAD_REQUEST, problem("Invalid request: validation failed, malformed body or unknown reference"))
                        .addResponses(ProblemResponses.UNAUTHORIZED, problem("Not signed in, the session expired, or the credentials are wrong"))
                        .addResponses(ProblemResponses.FORBIDDEN, problem("Missing or invalid CSRF token, or the action needs a role you do not have"))
                        .addResponses(ProblemResponses.NOT_FOUND, problem("The resource does not exist, or you are not a member of its workspace"))
                        .addResponses(ProblemResponses.CONFLICT, problem("The request conflicts with the current state of the resource"))
                        .addResponses(ProblemResponses.TOO_MANY_REQUESTS, problem("Too many authentication attempts; Retry-After gives the delay in seconds"))
                        .addResponses(ProblemResponses.INTERNAL_SERVER_ERROR, problem("Unexpected server error; use the request ID when reporting it")));
    }

    /**
     * Documents the security failures every operation can produce, so the
     * contracts only list their own business errors: 401 on operations that
     * need a session, 403 (CSRF) on every unsafe method.
     */
    @Bean
    OpenApiCustomizer securityProblemResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperationsMap().forEach((method, operation) -> {
            operation.getResponses().putIfAbsent("500", new ApiResponse().$ref(ProblemResponses.INTERNAL_SERVER_ERROR_REF));
            boolean needsSession = operation.getSecurity() == null || !operation.getSecurity().isEmpty();
            if (needsSession) {
                operation.getResponses().putIfAbsent("401", new ApiResponse().$ref(ProblemResponses.UNAUTHORIZED_REF));
            }
            if (method != PathItem.HttpMethod.GET) {
                operation.getResponses().putIfAbsent("403", new ApiResponse().$ref(ProblemResponses.FORBIDDEN_REF));
            }
        }));
    }

    /** Describe the same transport envelope applied by ApiResponseAdvice. */
    @Bean
    OpenApiCustomizer successResponseEnvelopes() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().forEach((code, response) -> {
                    if (!code.startsWith("2") || response.getContent() == null) return;
                    var json = response.getContent().get(MediaType.APPLICATION_JSON_VALUE);
                    if (json == null || json.getSchema() == null) return;
                    ObjectSchema envelope = new ObjectSchema();
                    envelope.addProperty("data", json.getSchema());
                    envelope.addProperty("meta", new Schema<>().$ref("#/components/schemas/ResponseMetadata"));
                    envelope.setRequired(List.of("data", "meta"));
                    json.setSchema(envelope);
                })));
    }

    private static ApiResponse problem(String description) {
        Schema<?> ref = new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA);
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType().schema(ref)));
    }

    private static ObjectSchema metadataSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("requestId", new StringSchema());
        schema.addProperty("timestamp", new StringSchema().format("date-time"));
        schema.setRequired(List.of("requestId", "timestamp"));
        return schema;
    }

    private static Schema<?> problemSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("RFC 9457 problem details");
        schema.addProperty("type", new StringSchema().format("uri-reference").example("about:blank"));
        schema.addProperty("title", new StringSchema().example("Bad Request"));
        schema.addProperty("status", new IntegerSchema().example(400));
        schema.addProperty("detail", new StringSchema().example("Validation failed"));
        schema.addProperty("instance", new StringSchema().format("uri-reference").example("/api/v1/workspaces"));
        schema.addProperty("requestId", new StringSchema().description("Correlates with X-Request-ID and backend logs"));
        schema.addProperty("timestamp", new StringSchema().format("date-time"));
        schema.addProperty("errors", new ObjectSchema()
                .description("Validation errors only: one message per invalid field")
                .additionalProperties(new StringSchema()));
        return schema;
    }
}
