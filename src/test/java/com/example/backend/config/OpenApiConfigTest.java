package com.example.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class OpenApiConfigTest {
    @Test void documentsWrappedSuccessesAndPreservesBodylessResponses() {
        var config = new OpenApiConfig();
        Schema<?> payload = new Schema<>().$ref("#/components/schemas/TaskView");
        var json = new io.swagger.v3.oas.models.media.MediaType().schema(payload);
        var responses = new ApiResponses()
                .addApiResponse("200", new ApiResponse().content(new Content().addMediaType(MediaType.APPLICATION_JSON_VALUE, json)))
                .addApiResponse("204", new ApiResponse().description("No content"));
        var operation = new Operation().responses(responses);
        var document = config.taskManagerOpenApi().paths(new Paths()
                .addPathItem("/api/v1/workspaces/1/tasks/42", new PathItem().get(operation)));
        config.successResponseEnvelopes().customise(document);
        config.securityProblemResponses().customise(document);

        Schema<?> envelope = json.getSchema();
        assertThat(envelope.getProperties().get("data")).isSameAs(payload);
        assertThat(envelope.getProperties().get("meta").get$ref()).isEqualTo("#/components/schemas/ResponseMetadata");
        assertThat(responses.get("204").getContent()).isNull();
        assertThat(responses.get("500").get$ref()).isEqualTo("#/components/responses/InternalServerError");
        Schema<?> problem = document.getComponents().getSchemas().get("Problem");
        assertThat(problem.getProperties())
                .containsKeys("requestId", "timestamp", "errors");
    }
}
